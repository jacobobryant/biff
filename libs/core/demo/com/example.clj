(ns com.example
  (:require [clojure.tools.namespace.repl :as tn-repl]
            [com.biffweb.core :as biff.core]
            [com.example.modules :refer [modules]]))

(defonce system (atom {}))

(def initial-system
  {:com.example/app-name "My Application"})

(def components
  [:com.example/config
   :com.example/webserver])

(defn start []
  (let [new-system (biff.core/start initial-system #'modules components)]
    (reset! system new-system))
  :started)

(defn stop []
  (biff.core/stop @system)
  (reset! system {})
  :stopped)

(defn refresh []
  (stop)
  (tn-repl/refresh :after `start)
  :refreshed)

(defn -main []
  (start)
  @(promise))

(biff.core/register
 {:com.example/app-name :string
  :com.example/routes   [:map-of [:tuple :keyword :string] 'ifn?]
  :com.example/handler  'ifn?
  :com.example/port     :int})
