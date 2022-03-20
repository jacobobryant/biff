(ns com.biffweb
  (:require [better-cond.core :as b]
            [buddy.core.nonce :as nonce]
            [buddy.sign.jwt :as jwt]
            [chime.core :as chime]
            [clj-http.client :as http]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.namespace.repl :as tn-repl]
            [com.biffweb.impl.extra :as extra]
            [com.biffweb.impl.middleware :as middle]
            [com.biffweb.impl.rum :as brum]
            [com.biffweb.impl.util :as util]
            [com.biffweb.impl.xtdb :as bxt]
            [hawk.core :as hawk]
            [muuntaja.middleware :as muuntaja]
            [reitit.ring :as reitit-ring]
            [ring.adapter.jetty9 :as jetty]))

(def pprint util/pprint)

(defonce system (atom nil))

(defn start-system
  "Starts a system from an initial system map.

  Stores the system in the biff.util/system atom. See
  See https://biff.findka.com/#system-composition and refresh."
  [system*]
  (reset! system (merge {:biff/stop '()} system*))
  (loop [{[f & components] :biff/components :as sys} system*]
    (when (some? f)
      (println "starting:" (str f))
      (recur (reset! system (f (assoc sys :biff/components components))))))
  (println "System started."))

(defn refresh
  "Stops the system, refreshes source files, and restarts the system.

  The system is stopped by calling all the functions in (:biff/stop
  @biff.util/system). (:after-refresh @system) is a fully-qualified symbol which
  will be resolved and called after refreshing.

  See start-system."
  []
  (let [{:keys [biff/after-refresh biff/stop]} @system]
    (doseq [f stop]
      (println "stopping:" (str f))
      (f))
    (tn-repl/refresh :after after-refresh)))

(defn eval-files! [{:keys [biff/eval-paths]
                    :or {eval-paths ["src"]}}]
  (swap! util/global-tracker util/eval-files* eval-paths)
  nil)

(defn use-hawk
  "A Biff component for Hawk. See https://github.com/wkf/hawk.

  on-save:       A single-argument function to call whenever a file is saved.
                 Receives the system map as a parameter. The function is called
                 no more than once every 500 milliseconds.
  paths:         A collection of root directories to monitor for file changes.
  exts:          If exts is non-empty, files that don't end in one of the
                 extensions will be ignored."
  [{:biff.hawk/keys [on-save exts paths]
    :or {paths ["src" "resources"]}
    :as sys}]
  (let [watch (hawk/watch!
                [(merge {:paths paths
                         ; todo debounce this properly
                         :handler (fn [{:keys [last-ran]
                                        :or {last-ran 0}} _]
                                    (when (< 500 (- (inst-ms (java.util.Date.)) last-ran))
                                      (on-save sys))
                                    {:last-ran (inst-ms (java.util.Date.))})}
                        (when exts
                          {:filter (fn [_ {:keys [^java.io.File file]}]
                                     (let [path (.getPath file)]
                                       (some #(str/ends-with? path %) exts)))}))])]
    (update sys :biff/stop conj #(hawk/stop! watch))))

(defn reitit-handler [{:keys [router routes on-error]
                       :or {on-error util/default-on-error}}]
  (reitit-ring/ring-handler
    (or router (reitit-ring/router routes))
    (reitit-ring/routes
      (reitit-ring/redirect-trailing-slash-handler)
      (reitit-ring/create-default-handler
        {:not-found          #(on-error (assoc % :status 404))
         :method-not-allowed #(on-error (assoc % :status 405))
         :not-acceptable     #(on-error (assoc % :status 406))}))))

(defn use-jetty
  "Starts a Jetty web server.

  websockets: A map from paths to handlers, e.g. {\"/api/chsk\" ...}."
  [{:biff/keys [host port handler]
    :biff.jetty/keys [quiet websockets]
    :or {host "localhost"
         port 8080}
    :as sys}]
  (let [server (jetty/run-jetty handler
                                {:host host
                                 :port port
                                 :join? false
                                 :websockets websockets
                                 :allow-null-path-info true})]
    (when-not quiet
      (println "Jetty running on" (str "http://" host ":" port)))
    (update sys :biff/stop conj #(jetty/stop-server server))))

(def wrap-anti-forgery-websockets middle/wrap-anti-forgery-websockets)
(def wrap-render-rum middle/wrap-render-rum)
(def wrap-index-files middle/wrap-index-files)
(def wrap-resource middle/wrap-resource)
(def wrap-internal-error middle/wrap-internal-error)
(def wrap-log-requests middle/wrap-log-requests)
(def wrap-ring-defaults middle/wrap-ring-defaults)
(def wrap-env middle/wrap-env)
(def wrap-inner-defaults middle/wrap-inner-defaults)

(defn use-outer-default-middleware [sys]
  (update sys :biff/handler middle/wrap-outer-defaults sys))

(defn read-config [path]
  (let [env (keyword (or (System/getenv "BIFF_ENV") "prod"))
        env->config (edn/read-string (slurp path))
        config-keys (concat (get-in env->config [env :merge]) [env])
        config (apply merge (map env->config config-keys))]
    config))

(defn use-config [sys]
  (merge sys (read-config (:biff/config sys))))

(defn generate-secret [length]
  (util/base64-encode (nonce/random-bytes length)))

(defn start-node [opts]
  (bxt/start-node opts))

(defn use-xt [sys]
  (bxt/use-xt sys))

(defn use-tx-listener [sys]
  (bxt/use-tx-listener sys))

(defn assoc-db [sys]
  (bxt/assoc-db sys))

(defn q [& args]
  (apply bxt/q args))

(defn lazy-q [& args]
  (apply bxt/lazy-q args))

(defn lookup [db k v]
  (bxt/lookup db k v))

(defn lookup-id [db k v]
  (bxt/lookup-id db k v))

(defn submit-tx [sys tx]
  (bxt/submit-tx sys tx))

(defn render [body]
  (brum/render body))

(defn unsafe
  "Return a map with :dangerouslySetInnerHTML, optionally merged into m."
  ([html] (brum/unsafe html))
  ([m html]
   (merge m (unsafe html))))

(def emdash brum/emdash)

(def endash brum/endash)

(def nbsp brum/nbsp)

(defn g-fonts [families]
  (brum/g-fonts families))

(defn base-html [opts & body]
  (apply brum/base-html opts body))

(defn form [opts & body]
  (apply brum/form opts body))

(defn export-rum [pages dir]
  (brum/export-rum pages dir))

(defn sh [& args]
  (apply util/sh args))

(defmacro catchall [& body]
  `(try ~@body (catch Exception ~'_ nil)))

(defn safe-merge [& ms]
  (reduce (fn [m1 m2]
            (let [dupes (filter #(contains? m1 %) (keys m2))]
              (when (not-empty dupes)
                (throw (ex-info (str "Maps contain duplicate keys: " (str/join ", " dupes))
                                {:keys dupes})))
              (merge m1 m2)))
          {}
          ms))

(defn normalize-email [email]
  (some-> email str/trim str/lower-case not-empty))

(defn mailersend [{:keys [mailersend/api-key
                          mailersend/defaults]}
                  opts]
  (let [opts (reduce (fn [opts [path x]]
                       (update-in opts path #(or % x)))
                     opts
                     defaults)]
    (try
      (get-in
        (http/post "https://api.mailersend.com/v1/email"
                   {:content-type :json
                    :oauth-token api-key
                    :form-params opts})
        [:headers "X-Message-Id"])
      (catch Exception e
        (println "mailersend failed:" (:body (ex-data e)))
        false))))

(defn random-uuid []
  (java.util.UUID/randomUUID))

(defn now []
  (java.util.Date.))

(defn anomaly? [x]
  (util/anomaly? x))

(defn anom [category & [message & [opts]]]
  (util/anom category message opts))

(def rfc3339 util/rfc3339)

(def parse-format-date util/parse-format-date)

(def parse-date util/parse-date)

(def format-date util/format-date)

(def crop-date util/crop-date)

(def crop-day util/crop-day)

(defn seconds-between [t1 t2]
  (util/seconds-between t1 t2))

(defn duration [x unit]
  (util/duration x unit))

(defn elapsed? [t1 t2 x unit]
  (util/elapsed? t1 t2 x unit))

(defn between-hours? [t h1 h2]
  (util/between-hours? t h1 h2))

(defn add-seconds [date seconds]
  (util/add-seconds date seconds))

(defn base64-encode [bs]
  (util/base64-encode bs))

(defn base64-decode [s]
  (util/base64-decode s))

(defn jwt-encrypt
  [claims secret]
  (jwt/encrypt
    (-> claims
        (assoc :exp (add-seconds (now) (:exp-in claims)))
        (dissoc :exp-in))
    (base64-decode secret)
    {:alg :a256kw :enc :a128gcm}))

(defn jwt-decrypt
  [token secret]
  (catchall
    (jwt/decrypt
      token
      (base64-decode secret)
      {:alg :a256kw :enc :a128gcm})))

(defn sha256 [string]
  (let [digest (.digest (java.security.MessageDigest/getInstance "SHA-256") (.getBytes string "UTF-8"))]
    (apply str (map (partial format "%02x") digest))))

(defn use-chime
  [{:biff.chime/keys [tasks] :as sys}]
  (reduce (fn [sys {:keys [schedule task]}]
            (let [scheduler (chime/chime-at (schedule) (fn [_] (task sys)))]
              (update sys :biff/stop conj #(.close scheduler))))
          sys
          tasks))

(defn use-when [f & components]
  (fn [sys]
    (if (f sys)
      (update sys :biff/components #(concat components %))
      sys)))

(defmacro fix-print [& body]
  `(binding [*out* (alter-var-root #'*out* identity)
             *err* (alter-var-root #'*err* identity)
             *flush-on-newline* (alter-var-root #'*flush-on-newline* identity)]
     ~@body))
