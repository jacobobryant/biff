(ns biff.components
  (:require
    [biff.auth :as auth]
    [biff.crux :as bcrux]
    [biff.http :as http]
    [biff.rules :as rules]
    [byte-transforms :as bt]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [crux.api :as crux]
    [expound.alpha :as expound]
    [immutant.web :as imm]
    [nrepl.server :as nrepl]
    [orchestra.spec.test :as st]
    [ring.middleware.anti-forgery :as anti-forgery]
    [ring.middleware.session.cookie :as cookie]
    [rum.core :as rum]
    [taoensso.sente :as sente]
    [taoensso.sente.server-adapters.immutant :refer [get-sch-adapter]]
    [taoensso.timbre :as timbre]
    [trident.util :as u])
  (:import [java.nio.file Paths]))

(defn wrap-env [handler {:keys [biff/node] :as sys}]
  (comp handler
    (fn [event-or-request]
      (let [req (:ring-req event-or-request event-or-request)]
        (-> (merge sys event-or-request)
          (assoc :biff/db (crux/db node))
          (merge (u/prepend-keys "session" (get req :session)))
          (merge (u/prepend-keys "params" (get req :params))))))))

(defn concrete [x]
  (cond
    (var? x) @x
    (fn? x) (x)
    :default x))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init [sys]
  (let [env (keyword (or (System/getenv "BIFF_ENV") :prod))
        config (some-> "config.edn"
                 u/maybe-slurp
                 edn/read-string
                 (u/merge-config env))
        {:biff/keys [first-start dev]
         :biff.init/keys [start-nrepl nrepl-port instrument timbre start-shadow]
         :or {start-nrepl true nrepl-port 7888 timbre true}
         :as sys} (merge sys config)]
    (when timbre
      (timbre/merge-config! (u/select-ns-as sys 'timbre nil)))
    (when instrument
      (s/check-asserts true)
      (st/instrument))
    (when (and first-start start-nrepl nrepl-port (not dev))
      (nrepl/start-server :port nrepl-port))
    (when (and first-start (or dev start-shadow))
      ((requiring-resolve 'shadow.cljs.devtools.server/start!)))
    sys))

(defn set-defaults [sys]
  (let [{:biff/keys [dev host rules triggers using-proxy]
         :keys [biff.web/port
                biff.static/root
                biff.static/root-dev]
         :or {port 8080}} sys
        using-proxy (cond
                      dev false
                      (some? using-proxy) using-proxy
                      :default (not (#{"localhost" nil} host)))
        root (or root "www")
        root-dev (if dev "www-dev" root-dev)]
    (merge
      {:biff/host "localhost"
       :biff.auth/on-signup "/signin-sent/"
       :biff.auth/on-signin-request "/signin-sent/"
       :biff.auth/on-signin-fail "/signin-fail/"
       :biff.auth/on-signin "/app/"
       :biff.auth/on-signout "/"
       :biff.crux/topology :jdbc
       :biff.crux/storage-dir "data/crux-db"
       :biff.web/port 8080
       :biff.static/root root
       :biff.static/resource-root "www"
       :biff.handler/secure-defaults true
       :biff.handler/not-found-path (str root "/404.html")}
      sys
      {:biff/rules #(rules/expand-ops (merge (concrete rules) rules/rules))
       :biff/triggers #(rules/expand-ops (concrete triggers))
       :biff.handler/roots (if root-dev
                             [root-dev root]
                             [root])
       :biff/base-url (if using-proxy
                        (str "https://" host)
                        (str "http://" host ":" port))}
      (when dev
        {:biff.crux/topology :standalone
         :biff.handler/secure-defaults false}))))

(defn start-crux [sys]
  (let [opts (-> sys
               (u/select-ns-as 'biff.crux 'crux)
               (set/rename-keys {:crux/topology :topology
                                 :crux/storage-dir :storage-dir}))
        node (bcrux/start-node opts)]
    (-> sys
      (assoc :biff/node node)
      (update :biff/stop conj #(.close node)))))

(defn start-sente [sys]
  (let [{:keys [ch-recv send-fn connected-uids
                ajax-post-fn ajax-get-or-ws-handshake-fn]}
        (sente/make-channel-socket! (get-sch-adapter)
          {:user-id-fn :client-id
           :csrf-token-fn (fn [{:keys [session/uid] :as req}]
                            ; Disable CSRF checks for anonymous users.
                            (if (some? uid)
                              (or (:anti-forgery-token req)
                                (get-in req [:session :csrf-token])
                                (get-in req [:session :ring.middleware.anti-forgery/anti-forgery-token])
                                (get-in req [:session "__anti-forgery-token"]))
                              (or
                                (get-in req [:params    :csrf-token])
                                (get-in req [:headers "x-csrf-token"])
                                (get-in req [:headers "x-xsrf-token"]))))})
        sente-route ["/api/chsk" {:get ajax-get-or-ws-handshake-fn
                                  :post ajax-post-fn
                                  :middleware [anti-forgery/wrap-anti-forgery]
                                  :name ::chsk}]]
    (-> sys
      (update :biff/routes conj sente-route)
      (assoc
        :biff/send-event send-fn
        :biff.sente/ch-recv ch-recv
        :biff.sente/connected-uids connected-uids))))

(defn start-tx-listener [{:keys [biff/node biff.sente/connected-uids] :as sys}]
  (let [last-tx-id (bcrux/with-tx-log [log {:node node}]
                     (atom (:crux.tx/tx-id (last log))))
        subscriptions (atom {})
        sys (assoc sys :biff.crux/subscriptions subscriptions)
        notify-tx-opts (-> sys
                         (merge (u/select-ns-as sys 'biff nil))
                         (assoc :last-tx-id last-tx-id))
        listener (crux/listen node {:crux/event-type :crux/indexed-tx}
                   (fn [ev] (bcrux/notify-tx notify-tx-opts)))]
    (add-watch connected-uids ::rm-subs
      (fn [_ _ old-uids new-uids]
        (let [disconnected (set/difference (:any old-uids) (:any new-uids))]
          (when (not-empty disconnected)
            (apply swap! subscriptions dissoc disconnected)))))
    (update sys :sys/stop conj #(.close listener))))

(defn wrap-event-handler [handler]
  (fn [{:keys [?reply-fn] :as event}]
    (u/fix-stdout
      (let [response (try
                       (handler event)
                       (catch Exception e
                         (.printStackTrace e)
                         (u/anom :fault)))]
        (when ?reply-fn
          (?reply-fn response))))))

(defn start-event-router [{:keys [biff.sente/ch-recv biff/event-handler]
                           :or {event-handler (constantly nil)} :as sys}]
  (update sys :sys/stop conj
    (sente/start-server-chsk-router! ch-recv
      (-> event-handler
        bcrux/wrap-sub
        bcrux/wrap-tx
        (wrap-env sys)
        wrap-event-handler)
      {:simple-auto-threading? true})))

(defn set-auth-route [sys]
  (update sys :biff/routes conj (auth/route sys)))

(defn set-handler [{:biff/keys [routes node]
                    :biff.handler/keys [roots
                                        secure-defaults
                                        spa-path
                                        not-found-path] :as sys}]
  (let [cookie-key (-> (assoc sys
                         :k :cookie-key
                         :biff/db (crux/db node))
                     auth/get-key
                     (bt/decode :base64))
        session-store (cookie/cookie-store {:key cookie-key})]
    (assoc sys :biff.web/handler
      (http/make-handler
        {:roots roots
         :session-store session-store
         :secure-defaults secure-defaults
         :not-found-path not-found-path
         :spa-path spa-path
         :routes [(into ["" {:middleware [[wrap-env sys]]}]
                    routes)]}))))

(defn copy-resources [src-root dest-root]
  (when-some [resource-root (io/resource src-root)]
    (let [files (->> resource-root
                  io/as-file
                  file-seq
                  (filter #(.isFile %)))]
      (doseq [src files
              :let [dest (.toFile
                           (.resolve
                             (Paths/get (.toURI (io/file dest-root)))
                             (.relativize
                               (Paths/get (.toURI resource-root))
                               (Paths/get (.toURI src)))))]]
        (io/make-parents dest)
        (io/copy (io/file src) (io/file dest))))))

; you could say that rum is one of our main exports
(defn export-rum [pages dir]
  (doseq [[path form] pages
          :let [full-path (cond-> (str dir path)
                            (str/ends-with? path "/") (str "index.html"))]]
    (io/make-parents full-path)
    (spit full-path (cond-> form
                      (not (string? form)) rum/render-static-markup))))

(defn write-static-resources
  [{:biff.static/keys [root resource-root]
    :keys [biff/static-pages] :as sys}]
  (export-rum static-pages root)
  (copy-resources resource-root root)
  sys)

(defn start-web-server [{:biff/keys [dev]
                         :biff.web/keys [handler host port]
                         :or {host "localhost"
                              port 8080} :as sys}]
  (let [host (if dev
               "0.0.0.0"
               host)
        server (imm/run handler {:host host :port port})]
    (update sys :sys/stop conj #(imm/stop server))))
