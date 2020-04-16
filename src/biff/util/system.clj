(ns biff.util.system
  (:require
    [biff.util :as bu]
    [biff.util.crux :as bu-crux]
    [biff.util.http :as bu-http]
    [biff.util.static :as bu-static]
    [biff.core :as core]
    [clojure.java.io :as io]
    [crux.api :as crux]
    [clojure.set :as set]))

(defn write-static-resources [{:keys [static-pages biff-config app-namespace]}]
  (let [static-root (bu/root biff-config app-namespace)]
    (bu-static/export-rum static-pages static-root)
    (bu-static/copy-resources (str "www/" app-namespace) static-root)
    {:static-root static-root}))

(defn start-crux [{:keys [storage-dir subscriptions]}]
  (let [node (bu-crux/start-node {:storage-dir storage-dir})
        _ (crux/sync node)
        last-tx-id (-> (bu-crux/tx-log {:node node}) ; Better way to do this?
                     last
                     :crux.tx/tx-id
                     atom)]
    (->
      (bu/pipe-fn
        (fn [opts]
          (update opts :tx #(crux/submit-tx node %)))
        #(bu/fix-stdout
           (bu-crux/notify-subscribers
             (assoc %
               :node node
               :subscriptions subscriptions
               :last-tx-id last-tx-id))))
      (set/rename-keys {:f :submit-tx :close :close-tx-pipe})
      (assoc :node node))))

(defn wrap-env [handler {:keys [node] :as env}]
  (comp handler #(-> %
                   (merge env)
                   (assoc :db (crux/db node)))))

(defn make-config [{:keys [app-namespace] :as config}]
  (bu/with-defaults config
    :debug core/debug
    :biff-config core/config
    :crux-dir (str "data/" app-namespace "/crux-db")
    :cookie-key-path (str "data/" app-namespace "/cookie-key")))

(defn start-biff [config]
  (let [{:keys [debug
                static-pages
                app-namespace
                event-handler
                route
                biff-config
                rules
                crux-dir
                cookie-key-path] :as config} (make-config config)
        {:keys [static-root]} (write-static-resources config)
        subscriptions (atom {})
        {:keys [submit-tx
                close-tx-pipe
                node]} (start-crux {:storage-dir crux-dir
                                    :subscriptions subscriptions})
        env {:subscriptions subscriptions
             :node node
             :rules rules
             :submit-tx submit-tx}
        {:keys [reitit-route
                close-router
                api-send
                connected-uids]} (bu-http/init-sente
                                   {:route-name ::chsk
                                    :handler (wrap-env event-handler env)})
        routes [["" {:middleware [[wrap-env env]]}
                 reitit-route
                 route]]
        handler (bu-http/make-handler
                  {:root static-root
                   :debug debug
                   :routes routes
                   :cookie-path cookie-key-path})]
    {:handler handler
     :node node
     :subscriptions subscriptions
     :submit-tx submit-tx
     :connected-uids connected-uids
     :api-send api-send
     :close (fn []
              (close-router)
              (close-tx-pipe)
              (.close node))}))

