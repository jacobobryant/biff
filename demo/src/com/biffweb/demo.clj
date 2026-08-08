(ns com.biffweb.demo
  (:require [clojure.tools.namespace.repl :as tn-repl]
            [com.biffweb.core :as biff.core]
            [com.biffweb.demo.components :refer [components]]
            [com.biffweb.demo.modules :refer [modules]]
            [nrepl.cmdline :as nrepl])
  (:gen-class))

(defonce system (atom {}))

(defn start []
  (let [new-system (biff.core/start #'modules components)]
    (reset! system new-system)
    new-system))

(defn stop []
  (biff.core/stop @system)
  (reset! system {})
  :stopped)

(defn refresh []
  (stop)
  (tn-repl/refresh :after `start)
  :done)

(defn -main [& _args]
  (let [{:biff.tasks/keys [nrepl-port]} (start)]
    (nrepl/-main "--port" nrepl-port
                 "--middleware" (pr-str '[cider.nrepl/cider-middleware])))
  @(promise))
