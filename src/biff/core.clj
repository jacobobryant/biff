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
  (let [{:keys [biff/dev biff.init/nrepl-port]}
        (reset! system
          (reduce (fn [sys component]
                    (component sys))
            (merge {:biff/stop '()} config)
            components))]
    (println)
    (println "System started.")
    (when dev
      (println)
      (println "Go to http://localhost:9630 -> \"Builds\" -> \"start watch\" -> \"Dashboard\".")
      (println "After the build finishes, go to http://localhost:8080.")
      (println "Connect your editor to nrepl port 7888.")
      (println "Also see `./task help` for a complete list of commands."))))

(defn start-spa [config]
  (start-system config
    [c/init
     c/set-defaults
     c/start-crux
     c/start-sente
     c/start-tx-listener
     c/start-event-router
     c/set-auth-route
     c/set-handler
     c/write-static-resources
     c/start-web-server
     c/start-jobs]))

; If you don't need ClojureScript/React, call this instead of start-spa.
(defn start-mpa [config]
  (start-system config
    [#(merge {:biff.init/start-nrepl true
              :biff.init/start-shadow false} %)
     c/init
     c/set-defaults
     #(dissoc % :biff.handler/spa-path)
     c/start-crux
     c/set-auth-route
     c/set-handler
     c/write-static-resources
     c/start-web-server
     c/start-jobs]))
