(ns biff.system
  (:require
    [biff.http :as http]
    [biff.auth :as auth]
    [biff.rules :as rules]
    [byte-transforms :as bt]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [crux.api :as crux]
    [expound.alpha :as expound]
    [taoensso.sente :as sente]
    [ring.middleware.session.cookie :as cookie]
    [ring.middleware.anti-forgery :as anti-forgery]
    [rum.core :as rum]
    [taoensso.sente.server-adapters.immutant :refer [get-sch-adapter]]
    [biff.crux :as bcrux]
    [taoensso.timbre :refer [log spy]]
    [trident.util :as u])
  (:import
    [java.nio.file Paths]))

(defn wrap-env [handler {:keys [biff/node] :as sys}]
  (comp handler
    (fn [event-or-request]
      (let [req (:ring-req event-or-request event-or-request)]
        (-> (merge sys event-or-request)
          (assoc :biff/db (crux/db node))
          (merge (u/prepend-keys "session" (get req :session)))
          (merge (u/prepend-keys "params" (get req :params))))))))

(defn set-defaults [sys app-ns]
  (let [sys (merge sys (u/select-ns-as sys (str app-ns ".biff") 'biff))
        {:biff/keys [dev host rules triggers]
         :keys [biff.auth/send-email
                biff.web/port
                biff.static/root
                biff.static/root-dev]
         :or {port 8080}} sys
        root (or root (str "www/" host))
        root-dev (if dev "www-dev" root-dev)]
    (merge
      {:biff.crux/topology :jdbc
       :biff.crux/storage-dir (str "data/" app-ns "/crux-db")
       :biff.web/port 8080
       :biff.static/root root
       :biff.static/resource-root (str "www/" app-ns)
       :biff.handler/secure-defaults true
       :biff.handler/not-found-path (str root "/404.html")}
      sys
      {:biff/rules (rules/expand-ops (merge rules rules/rules))
       :biff/triggers (rules/expand-ops triggers)
       :biff.handler/roots (if root-dev
                             [root-dev root]
                             [root])
       :biff/base-url (if (= host "localhost")
                        (str "http://localhost:" port)
                        (str "https://" host))}
      (when dev
        {:biff.crux/topology :standalone
         :biff.handler/secure-defaults false}))))

(defn check-config [sys]
  (when-not (contains? sys :biff/host)
    (throw (ex-info ":biff/host not set. Do you need to add or update config.edn?" {})))
  sys)

(defn start-crux [sys]
  (let [opts (-> sys
               (u/select-ns-as 'biff.crux 'crux)
               (set/rename-keys {:crux/topology :topology
                                 :crux/storage-dir :storage-dir}))
        node (bcrux/start-node opts)]
    (-> sys
      (assoc :biff/node node)
      (update :sys/stop conj #(.close node)))))

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

(defn set-handler [{:biff/keys [routes host node]
                    :biff.handler/keys [roots
                                        secure-defaults
                                        spa-path
                                        not-found-path] :as sys}]
  (let [cookie-key (bt/decode (auth/get-key (assoc sys
                                              :k :cookie-key
                                              :biff/db (crux/db node)))
                     :base64)
        session-store (cookie/cookie-store {:key cookie-key})
        handler (http/make-handler
                  {:roots roots
                   :session-store session-store
                   :secure-defaults secure-defaults
                   :not-found-path not-found-path
                   :spa-path spa-path
                   :routes [(into ["" {:middleware [[wrap-env sys]]}]
                              routes)]})]
    (update sys :biff.web/host->handler assoc host handler)))

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
  (copy-resources resource-root root))

(defn start-biff [sys app-ns]
  (binding [s/*explain-out* expound/printer]
    (let [new-sys (-> sys
                    (set-defaults app-ns)
                    check-config
                    start-crux
                    start-sente
                    start-tx-listener
                    start-event-router
                    set-auth-route
                    set-handler)]
      (write-static-resources new-sys)
      (-> sys
        (merge (select-keys new-sys [:sys/stop :biff.web/host->handler]))
        (merge (u/select-ns-as new-sys 'biff (str app-ns ".biff")))))))
