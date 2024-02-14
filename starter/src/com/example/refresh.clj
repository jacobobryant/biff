(ns com.example.refresh
  (:require [com.biffweb :as biff]
            [ring.adapter.jetty9 :as jetty]
            [rum.core :as rum]))

(defn send-message! [{:keys [biff/dev-clients]} content]
  (let [html (rum/render-static-markup
              [:div#biff-refresh {:hx-swap-oob "innerHTML"}
               content])]
    (doseq [ws @dev-clients]
      (jetty/send! ws html))))

(defn handle-eval-result! [{:keys [biff/refresh-on-save] :as ctx}
                           {:clojure.tools.namespace.reload/keys [error error-ns]}]
  (cond
    (not refresh-on-save) nil
    (some? error) (send-message! ctx (str "Compilation error in namespace " error-ns ": "
                                          (.getMessage (.getCause err))))
    :else (send-message! ctx [:script (biff/unsafe "location.reload()")])))

(defn snippet [ctx]
  [:div#biff-refresh {:hx-ext "ws" :ws-connect "/biff/dev"}])

(defn ws-handler [{:keys [biff/dev-clients] :as ctx}]
  {:status 101
   :headers {"upgrade" "websocket"
             "connection" "upgrade"}
   :ws {:on-connect (fn [ws]
                      (swap! dev-clients conj ws))
        :on-close (fn [ws status-code reason]
                    (swap! dev-clients disj ws))}})

(defn wrap-refresh-enabled [handler]
  (fn [{:keys [biff/refresh-on-save] :as ctx}]
    (when refresh-on-save
      (handler ctx))))

(def module
  {:routes ["" {:middleware [wrap-refresh-enabled]}
            ["/biff/dev" {:get ws-handler}]]})
