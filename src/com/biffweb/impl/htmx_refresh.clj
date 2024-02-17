(ns com.biffweb.impl.htmx-refresh
  (:require [com.biffweb.impl.rum :as brum]
            [clojure.string :as str]
            [ring.adapter.jetty9 :as jetty]
            [ring.util.response :as ru-response]
            [rum.core :as rum]))

(defn send-message! [{:keys [biff.refresh/clients]} content]
  (let [html (rum/render-static-markup
              [:div#biff-refresh {:hx-swap-oob "innerHTML"}
               content])]
    (doseq [ws @clients]
      (jetty/send! ws html))))

(defn ws-handler [{:keys [biff.refresh/clients] :as ctx}]
  {:status 101
   :headers {"upgrade" "websocket"
             "connection" "upgrade"}
   :ws {:on-connect (fn [ws]
                      (swap! clients conj ws))
        :on-close (fn [ws status-code reason]
                    (swap! clients disj ws))}})

(def snippet
  (str (rum/render-static-markup
        [:div#biff-refresh {:hx-ext "ws"
                            :ws-connect "/__biff/refresh"}])
       "</body>"))

(defn insert-refresh-snippet [{:keys [body] :as response}]
  (if-let [body-str (and (str/includes? (or (ru-response/get-header response "content-type") "") "text/html")
                         (cond
                           (string? body) body
                           (#{java.io.InputStream java.io.File} (type body)) (slurp body)))]
    (-> response
        (assoc :body (str/replace body-str "</body>" snippet))
        (update :headers dissoc (some-> (ru-response/find-header response "content-length") key)))
    response))

(defn wrap-htmx-refresh [handler]
  (fn [{:keys [uri] :as ctx}]
    (if (= uri "/__biff/refresh")
      (ws-handler ctx)
      (insert-refresh-snippet (handler ctx)))))

(defn send-refresh-command [ctx {:clojure.tools.namespace.reload/keys [error error-ns]}]
  (send-message! ctx (if (some? error)
                       [:script (assoc (brum/unsafe "alert(document.querySelector('[data-biff-refresh-message]').getAttribute('data-biff-refresh-message'))")
                                       :data-biff-refresh-message
                                       (str "Compilation error in namespace " error-ns ": "
                                            (.getMessage (.getCause error))))]
                       [:script (brum/unsafe "location.reload()")])))

(defn use-htmx-refresh [{:keys [biff/handler biff.refresh/enabled] :as ctx}]
  (if-not enabled
    ctx
    (-> ctx
        (assoc :biff.refresh/clients (atom #{}))
        (update :biff/handler wrap-htmx-refresh)
        (update :biff.eval/on-eval conj #'send-refresh-command))))
