(ns {{main-ns}}
  (:require
    [biff.crux :as bcrux]
    [biff.middleware :as mid]
    [biff.misc :as misc]
    [biff.util :as bu]
    [{{parent-ns}}.handlers :refer [api]]
    [{{parent-ns}}.routes.auth :refer [wrap-authentication]]
    [{{parent-ns}}.env :refer [use-env]]
    [{{parent-ns}}.routes :refer [routes on-error]]
    [{{parent-ns}}.rules :refer [schema]])
  (:gen-class))

; See also:
; - https://biff.findka.com/#system-composition
; - https://biff.findka.com/codox/biff.util.html#var-start-system

(def components
  [use-env
   misc/use-nrepl
   bcrux/use-crux
   #(update % :biff.sente/event-handler bcrux/wrap-db)
   misc/use-sente
   bcrux/use-crux-sub-notifier
   misc/use-reitit
   #(update % :biff/handler bcrux/wrap-db)
   #(update % :biff/handler wrap-authentication)
   mid/use-default-middleware
   #(assoc % :biff.jetty/websockets
           {"/api/chsk" (:biff.handler %)})
   misc/use-jetty])

; routes, on-error and schema are defined as anonymous functions to facilitate
; late-binding: if you redefine them, you don't have to call biff.util/refresh
; for the changes to take place.
(def config {:biff.middleware/spa-path "/app/"
             :biff.reitit/routes       (fn [] (routes))
             :biff.sente/event-handler (fn [event] (api event (:?data event)))
             :biff/after-refresh       `-main
             :biff/on-error            (fn [req] (on-error req))
             :biff/schema              (fn [] schema)})

(defn -main []
  (bu/start-system config components))
