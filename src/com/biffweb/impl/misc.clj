(ns com.biffweb.impl.misc
  (:require [buddy.core.nonce :as nonce]
            [buddy.sign.jwt :as jwt]
            [chime.core :as chime]
            [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.biffweb.impl.time :as time]
            [com.biffweb.impl.util :as util]
            [com.biffweb.impl.xtdb :as bxt]
            [nextjournal.beholder :as beholder]
            [reitit.ring :as reitit-ring]
            [ring.adapter.jetty :as jetty]))

(defn use-beholder [{:biff.beholder/keys [on-save exts paths enabled]
                     :or {paths ["src" "resources" "test"]
                          enabled true}
                     :as ctx}]
  (if-not enabled
    ctx
    (let [;; Poor man's debouncer -- don't want to pull in core.async just for
          ;; this, and don't want to spend time figuring out how else to do it.
          last-called (atom (java.util.Date.))
          watch (apply beholder/watch
                       (fn [{:keys [path]}]
                         (when (and (or (empty? exts)
                                        (some #(str/ends-with? path %) exts))
                                    (time/elapsed? @last-called :now 1 :seconds))
                           ;; Give all the files some time to get written before invoking the callback.
                           (Thread/sleep 100)
                           (util/catchall-verbose (on-save ctx))
                           (reset! last-called (java.util.Date.))))
                       paths)]
      (update ctx :biff/stop conj #(beholder/stop watch)))))

;; Deprecated
(defn use-hawk [{:biff.hawk/keys [on-save exts paths]
                 :or {paths ["src" "resources"]}
                 :as ctx}]
  (use-beholder (merge {:biff.beholder/on-save on-save
                        :biff.beholder/exts exts
                        :biff.beholder/paths paths}
                       ctx)))

(defn reitit-handler [{:keys [router routes on-error]}]
  (let [make-error-handler (fn [status]
                             (fn [ctx]
                               ((or on-error
                                    (:biff.middleware/on-error ctx on-error)
                                    util/default-on-error)
                                (assoc ctx :status status))))]
    (reitit-ring/ring-handler
     (or router (reitit-ring/router routes))
     (reitit-ring/routes
      (reitit-ring/redirect-trailing-slash-handler)
      (reitit-ring/create-default-handler
       {:not-found          (make-error-handler 404)
        :method-not-allowed (make-error-handler 405)
        :not-acceptable     (make-error-handler 406)})))))

(defn use-jetty [{:biff/keys [host port handler]
                  :or {host "localhost"
                       port 8080}
                  :as ctx}]
  (let [server (jetty/run-jetty (fn [req]
                                  (handler (merge (bxt/merge-context ctx) req)))
                                {:host host
                                 :port port
                                 :join? false})]
    (log/info "Jetty running on" (str "http://" host ":" port))
    (update ctx :biff/stop conj #(.stop server))))

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
        (log/error e "MailerSend exception")
        false))))

(defn jwt-encrypt
  [claims secret]
  (jwt/encrypt
   (-> claims
       (assoc :exp (time/add-seconds (time/now) (:exp-in claims)))
       (dissoc :exp-in))
   (util/base64-decode secret)
   {:alg :a256kw :enc :a128gcm}))

(defn jwt-decrypt
  [token secret]
  (try
    (jwt/decrypt
     token
     (util/base64-decode secret)
     {:alg :a256kw :enc :a128gcm})
    (catch Exception _
      nil)))

(defn use-chime
  [{:keys [biff/features biff/plugins biff/modules biff.chime/tasks] :as ctx}]
  (reduce (fn [ctx {:keys [schedule task error-handler]}]
            (let [f (fn [_] (task (bxt/merge-context ctx)))
                  opts (when error-handler {:error-handler error-handler})
                  scheduler (chime/chime-at (schedule) f opts)]
              (update ctx :biff/stop conj #(.close scheduler))))
          ctx
          (or tasks
              (some->> (or modules plugins features) deref (mapcat :tasks)))))

(defn generate-secret [length]
  (let [buffer (byte-array length)]
    (.nextBytes (java.security.SecureRandom/getInstanceStrong) buffer)
    (.encodeToString (java.util.Base64/getEncoder) buffer)))

(defn use-random-default-secrets [ctx]
  (merge ctx
         (when (nil? (:biff.middleware/cookie-secret ctx))
           (log/warn ":biff.middleware/cookie-secret is empty, using random value")
           {:biff.middleware/cookie-secret (generate-secret 16)})
         (when (nil? (:biff/jwt-secret ctx))
           (log/warn ":biff/jwt-secret is empty, using random value")
           {:biff/jwt-secret (generate-secret 32)})))

(defn get-secret [ctx k]
  (some-> (get ctx k)
          (System/getenv)
          not-empty))

(defn use-secrets [ctx]
  (when-not (every? #(get-secret ctx %) [:biff.middleware/cookie-secret :biff/jwt-secret])
    (binding [*out* *err*]
      (println "Secrets are missing. Run `bb generate-secrets` and edit secrets.env.")
      (System/exit 1)))
  (assoc ctx :biff/secret #(get-secret ctx %)))

(defn doc-schema [{:keys [required optional closed wildcards]
                   :or {closed true}}]
  (let [ks (->> (concat required optional)
                (map #(cond-> % (not (keyword? %)) first)))
        schema (vec (concat [:map {:closed (and (not wildcards) closed)}]
                            required
                            (for [x optional
                                  :let [[k & rst] (if (keyword? x)
                                                    [x]
                                                    x)
                                        [opts rst] (if (map? (first rst))
                                                     [(first rst) (rest rst)]
                                                     [{} rst])
                                        opts (assoc opts :optional true)]]
                              (into [k opts] rst))))
        schema (if-not wildcards
                 schema
                 [:and
                  schema
                  [:fn (fn [doc]
                         (every? (fn [[k v]]
                                   (if-let [v-pred (and (keyword? k)
                                                        (wildcards (symbol (namespace k))))]
                                     (v-pred v)
                                     (not closed)))
                                 (apply dissoc doc ks)))]])]
    schema))
