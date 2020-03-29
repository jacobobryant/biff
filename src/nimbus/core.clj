(ns nimbus.core
  (:require
    [clojure.edn :as edn]
    [clojure.java.classpath :as cp]
    [clojure.java.io :as io]
    [clojure.reflect :refer [reflect]]
    [immutant.web :as imm]
    [mount.core :as mount :refer [defstate]]
    [muuntaja.middleware :as muuntaja]
    [nimbus.lib :as lib]
    [reitit.ring :as reitit]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.middleware.file :refer [wrap-file]]
    [ring.middleware.not-modified :refer [wrap-not-modified]]
    [ring.middleware.resource :refer [wrap-resource]]
    [taoensso.sente :as sente]
    [ring.middleware.keyword-params]
    [ring.middleware.params]
    [trident.ring :as tring]
    [taoensso.sente.server-adapters.immutant :refer [get-sch-adapter]]
    [trident.util :as u]))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {:user-id-fn :client-id
                                                     :csrf-token-fn nil})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv)
  (def chsk-send!                    send-fn)
  (def connected-uids                connected-uids))

(def app
  (reitit/ring-handler
    (reitit/router
      [["/chsk" {:get ring-ajax-get-or-ws-handshake
                 :post ring-ajax-post
                 :middleware [ring.middleware.params/wrap-params
                              ring.middleware.keyword-params/wrap-keyword-params]
                 :name ::chsk}]])
    (reitit/routes
      (reitit/create-resource-handler {:path "/"})
      (reitit/create-default-handler))))

(defn init []
  (doseq [root (cp/classpath)
          f (file-seq root)
          :when (= "nimbus.edn" (.getName f))
          :let [{nspace :ns} (u/catchall (edn/read-string (slurp f)))]
          :when (some? nspace)]
    (require nspace)))

(defstate server
  :start (do
           (init)
           (imm/run app {:port 8080}))
  :stop (imm/stop server))

(defmulti api :id)

(defmethod api :default
  [_ _]
  :nimbus/not-found)

(defn wrap-api [{:keys [?data ?reply-fn] :as event}]
  (some->>
    (with-out-str
      (let [response (try
                       (api event ?data)
                       (catch Exception e
                         (.printStackTrace e)
                         :nimbus/exception))]
        (when ?reply-fn
          (?reply-fn response))))
    not-empty
    (.print System/out)))

(defstate sente-router
  :start (sente/start-server-chsk-router! ch-chsk wrap-api)
  :stop (sente-router))

(defn -main []
  (mount/start #'server #'sente-router))
