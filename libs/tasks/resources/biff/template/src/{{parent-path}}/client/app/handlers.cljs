(ns {{parent-ns}}.client.app.handlers
  (:require [clojure.pprint :as pp]))

(defmulti api (comp first :?data))
(defmethod api :default
  [{[event-id] :?data} data]
  (println "unhandled event:" event-id))

(defmethod api :biff/error
  [_ anom]
  (pp/pprint anom))
