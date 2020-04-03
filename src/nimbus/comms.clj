(ns ^:nimbus nimbus.comms
  (:require
    [immutant.web :as imm]
    [mount.core :as mount :refer [defstate]]
    [ring.middleware.defaults :refer [wrap-defaults site-defaults secure-site-defaults]]
    [ring.middleware.session.cookie :refer [cookie-store]]
    [crypto.random :as random]
    [reitit.ring :as reitit]
    [taoensso.sente :as sente]
    [ring.middleware.keyword-params]
    [ring.middleware.params]
    [byte-transforms :as bt]
    [nimbus.core :refer [config]]
    [taoensso.sente.server-adapters.immutant :refer [get-sch-adapter]]))

(defmulti api :id)

(defmethod api :default
  [_ _]
  ::not-found)

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {:user-id-fn :client-id})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv)
  (def api-send                      send-fn)
  (def connected-uids                connected-uids))

(def secret-key (bt/decode "Eof0_YC632HxXOOBgmEORg" :base64))

(defn app [routes]
  (reitit/ring-handler
    (reitit/router
      (into [["/nimbus/comms/chsk" {:get ring-ajax-get-or-ws-handshake
                                    :post ring-ajax-post
                                    :middleware [ring.middleware.params/wrap-params
                                                 ring.middleware.keyword-params/wrap-keyword-params]
                                    :name ::chsk}]]
        routes)
      {:data {:middleware [[wrap-defaults
                            (assoc-in site-defaults
                              [:session :store] (cookie-store {:key secret-key}))]]}})
    (reitit/routes
      (reitit/create-resource-handler {:path "/"})
      (reitit/create-default-handler))))

(defn wrap-api [{:keys [?data ?reply-fn] :as event}]
  (some->>
    (with-out-str
      (let [event (merge event (get-in event [:ring-req :session]))
            response (try
                       (api event ?data)
                       (catch Exception e
                         (.printStackTrace e)
                         ::exception))]
        (when ?reply-fn
          (?reply-fn response))))
    not-empty
    (.print System/out)))

(defstate system
  :start {:server (imm/run
                    (app (->> config
                           vals
                           (map ::route)
                           (filterv some?)))
                    {:port 8080})
          :router (sente/start-server-chsk-router! ch-chsk wrap-api)}
  :stop (let [{:keys [server router]} system]
          (imm/stop server)
          (router)))
