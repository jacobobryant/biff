(ns {{parent-ns}}.core
  (:require
    [biff.misc :as misc]
    [biff.util :as bu]
    [biff.crux :as bcrux]
    [biff.middleware :as mid]
    [{{parent-ns}}.handlers :refer [api]]
    [{{parent-ns}}.routes.auth :refer [wrap-authentication]]
    [{{parent-ns}}.config :refer [use-config]]
    [{{parent-ns}}.routes :refer [routes on-error]]
    [{{parent-ns}}.rules :refer [schema]])
  (:gen-class))

(def components
  [use-config
   misc/use-nrepl
   bcrux/use-crux
   (fn [sys]
     (update sys :biff.sente/event-handler bcrux/wrap-db))
   misc/use-sente
   bcrux/use-crux-sub
   misc/use-reitit
   (fn [sys]
     (update sys :biff/handler bcrux/wrap-db))
   (fn [sys]
     (update sys :biff/handler wrap-authentication))
   mid/use-default-middleware
   (fn [{:keys [biff/handler] :as sys}]
     (assoc sys :biff.jetty/websockets {"/api/chsk" handler}))
   misc/use-jetty])

(def config {:biff/schema              (fn [] schema)
             :biff.reitit/routes       (fn [] (routes))
             :biff/on-error            (fn [req] (on-error req))
             :biff.sente/event-handler (fn [event] (api event (:?data event)))
             :biff.middleware/spa-path "/app/"
             :biff/after-refresh       `-main})

(defn -main []
  (bu/start-system config components))
