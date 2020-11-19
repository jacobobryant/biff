(ns biff.core
  (:require
    [biff.components :as c]
    [clojure.tools.namespace.repl :as tn-repl]))

(defonce system (atom nil))

(defn refresh []
  (let [{:keys [biff/after-refresh biff/stop]} @system]
    (doseq [f stop]
      (f))
    (tn-repl/refresh :after after-refresh)))

(defn start-system [config components]
  (reset! system
    (reduce (fn [sys component]
              (component sys))
      (merge {:biff/stop '()} config)
      components)))

(def default-spa-components
  [c/init
   c/set-defaults
   c/start-crux
   c/start-sente
   c/start-tx-listener
   c/start-event-router
   c/set-auth-route
   c/set-http-handler
   c/start-web-server
   c/write-static-resources
   c/start-jobs
   c/print-spa-help])

(def default-mpa-components
  [#(merge {:biff.init/start-nrepl true
            :biff.init/start-shadow false} %)
   c/init
   c/set-defaults
   #(dissoc % :biff.http/spa-path)
   c/start-crux
   c/set-auth-route
   c/set-http-handler
   c/start-web-server
   c/write-static-resources
   c/start-jobs
   c/print-mpa-help])

; Deprecated. Just call start-system and supply default-*-components.

(defn start-spa [config]
  (start-system config default-spa-components))

(defn start-mpa [config]
  (start-system config default-mpa-components))
