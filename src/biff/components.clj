(ns biff.components
  (:require
    [biff.auth :as auth]
    [biff.crux :as bcrux]
    [biff.http :as http]
    [biff.rules :as rules]
    [biff.util :as bu]
    [byte-transforms :as bt]
    [chime.core :as chime]
    [clojure.core.async :as async]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [crux.api :as crux]
    [expound.alpha :as expound]
    [nrepl.server :as nrepl]
    [ring.adapter.jetty9 :as jetty]
    [ring.middleware.anti-forgery :as anti-forgery]
    [ring.middleware.session.cookie :as cookie]
    [rum.core :as rum]
    [taoensso.sente :as sente]
    [taoensso.sente.server-adapters.jetty9 :refer [get-sch-adapter]])
  (:import
    [java.nio.file Paths]))

(defn wrap-env [handler {:keys [biff/node] :as sys}]
  (comp handler
    (fn [event-or-request]
      (let [req (:ring-req event-or-request event-or-request)]
        (-> (merge sys event-or-request)
          (assoc :biff/db (crux/db node))
          (merge (bu/prepend-keys "session" (get req :session)))
          (merge (bu/prepend-keys "params" (get req :params))))))))

(defn merge-config [config env]
  (let [env-order (concat (get-in config [env :inherit]) [env])]
    (apply merge (map config env-order))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init [sys]
  (let [env (keyword (or (System/getenv "BIFF_ENV") :prod))
        unmerged-config (bu/catchall (edn/read-string (slurp "config/main.edn")))
        config (some-> unmerged-config (merge-config env))
        {:biff/keys [first-start dev]
         :biff.init/keys [start-nrepl start-shadow]
         :as sys} (merge sys config {:biff/unmerged-config unmerged-config})]
    (let [start-nrepl (if (some? start-nrepl) start-nrepl (not dev))
          start-shadow (if (some? start-shadow) start-shadow dev)]
      (when (and first-start start-nrepl)
        (nrepl/start-server :port 7888))
      (when (and first-start start-shadow)
        ; Without this, I sometimes get the following when loading localhost:9630:
        ; java.lang.Exception: Unable to resolve spec: :edn-query-language.core/property
        (require 'edn-query-language.core)
        ((requiring-resolve 'shadow.cljs.devtools.server/start!)))
      sys)))

(defn set-defaults [sys]
  (let [{:biff/keys [dev host rules triggers]
         :keys [biff.web/port]
         :or {port 8080}} sys
        host (or (when-not dev host) "localhost")
        using-proxy (if dev
                      false
                      (not= "localhost" host))]
    (merge
      {:biff.auth/on-signup "/signin-sent"
       :biff.auth/on-signin-request "/signin-sent"
       :biff.auth/on-signin-fail "/signin-fail"
       :biff.auth/on-signin "/app"
       :biff.auth/on-signout "/"
       :biff.crux/topology :standalone
       :biff.http/secure-defaults true
       :biff.http/not-found-path "/404.html"
       :biff.http/spa-path "/app/index.html"
       :biff.http/asset-paths #{"/cljs/" "/js/" "/css/"}
       :biff.web/host (if dev "0.0.0.0" "localhost")
       :biff.web/port port}
      sys
      {:biff/host host
       :biff/rules #(rules/expand-ops (merge (bu/concrete rules) rules/rules))
       :biff/triggers #(rules/expand-ops (bu/concrete triggers))
       :biff/base-url (if using-proxy
                        (str "https://" host)
                        (str "http://" host ":" port))
       :biff.static/root "www"
       :biff.static/resource-root "www"
       :biff.static/dev-root "www-dev"}
      (when dev
        {:biff.crux/topology :standalone
         :biff.http/secure-defaults false}))))

(defn start-crux [{:keys [biff.crux/topology] :as sys}]
  (let [index-store {:kv-store {:crux/module 'crux.rocksdb/->kv-store
                                :db-dir (io/file "data/crux-db/index")}}
        node (crux/start-node
               (case topology
                 :standalone
                 {:crux/index-store index-store
                  :rocksdb-golden {:crux/module 'crux.rocksdb/->kv-store
                                   :db-dir (io/file "data/crux-db/tx-log-doc-store")}
                  :crux/document-store {:kv-store :rocksdb-golden}
                  :crux/tx-log {:kv-store :rocksdb-golden}}

                 :jdbc
                 {:crux/index-store index-store
                  :crux.jdbc/connection-pool {:dialect {:crux/module 'crux.jdbc.psql/->dialect}
                                              :db-spec (bu/select-ns-as sys 'biff.crux.jdbc nil)}
                  :crux/tx-log {:crux/module 'crux.jdbc/->tx-log
                                :connection-pool :crux.jdbc/connection-pool}
                  :crux/document-store {:crux/module 'crux.jdbc/->document-store
                                        :connection-pool :crux.jdbc/connection-pool}}))]
    (crux/sync node)
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
      (update :biff/stop conj #(async/close! ch-recv))
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
                         (merge (bu/select-ns-as sys 'biff nil))
                         (assoc :last-tx-id last-tx-id))
        listener (crux/listen node {:crux/event-type :crux/indexed-tx}
                   (fn [ev] (bcrux/notify-tx notify-tx-opts)))]
    (add-watch connected-uids ::rm-subs
      (fn [_ _ old-uids new-uids]
        (let [disconnected (set/difference (:any old-uids) (:any new-uids))]
          (when (not-empty disconnected)
            (apply swap! subscriptions dissoc disconnected)))))
    (update sys :biff/stop conj #(.close listener))))

(defn wrap-event-handler [handler]
  (fn [{:keys [?reply-fn] :as event}]
    (bu/fix-stdout
      (let [response (try
                       (handler event)
                       (catch Exception e
                         (.printStackTrace e)
                         (bu/anom :fault)))]
        (when ?reply-fn
          (?reply-fn response))))))

(defn start-event-router [{:keys [biff.sente/ch-recv biff/event-handler]
                           :or {event-handler (constantly nil)} :as sys}]
  (update sys :biff/stop conj
    (sente/start-server-chsk-router! ch-recv
      (-> event-handler
        bcrux/wrap-sub
        bcrux/wrap-tx
        (wrap-env sys)
        wrap-event-handler)
      {:simple-auto-threading? true})))

(defn set-auth-route [sys]
  (update sys :biff/routes conj (auth/route sys)))

(defn set-http-handler
  [{:keys [biff/routes
           biff/node
           biff.static/root
           biff.static/dev-root
           biff.http/secure-defaults
           biff.http/spa-path
           biff.http/not-found-path
           biff.http/asset-paths] :as sys}]
  (let [cookie-key (-> (assoc sys
                         :k :cookie-key
                         :biff/db (crux/db node))
                     auth/get-key
                     (bt/decode :base64))
        session-store (cookie/cookie-store {:key cookie-key})]
    (assoc sys :biff.web/handler
      (http/make-handler
        {:root root
         :dev-root dev-root
         :session-store session-store
         :secure-defaults secure-defaults
         :not-found-path not-found-path
         :spa-path spa-path
         :asset-paths asset-paths
         :routes [(into ["" {:middleware [[wrap-env sys]]}]
                    routes)]}))))

(defn start-web-server [{:biff.web/keys [handler host port] :as sys}]
  (let [server (jetty/run-jetty handler
                 {:host host
                  :port port
                  :join? false
                  :websockets {"/api/chsk" handler}
                  :allow-null-path-info true})]
    (update sys :biff/stop conj #(jetty/stop-server server))))

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

(defn start-jobs [{:keys [biff/jobs] :as sys}]
  (update sys :biff/stop into
    (for [{:keys [offset-minutes period-minutes job-fn]} jobs]
      (let [closeable (chime/chime-at
                        (->> (bu/add-seconds (java.util.Date.) (* 60 offset-minutes))
                          (iterate #(bu/add-seconds % (* period-minutes 60)))
                          (map #(.toInstant %)))
                        (fn [_] (job-fn sys)))]
        #(.close closeable)))))

(defn print-spa-help [{:keys [biff/dev] :as sys}]
  (when dev
    (println)
    (println "Go to http://localhost:9630/builds -> \"start watch\" -> \"Dashboard\".")
    (println "After the build finishes, go to http://localhost:8080.")
    (println "Connect your editor to nrepl port 7888.")
    (println "See `./task help` for a complete list of commands.")
    (println))
  sys)

(defn print-mpa-help [{:keys [biff/dev] :as sys}]
  (when dev
    (println)
    (println "Go to http://localhost:8080. Connect your editor to nrepl port 7888.")
    (println "See `./task help` for a complete list of commands.")
    (println))
  sys)
