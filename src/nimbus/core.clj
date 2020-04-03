(ns nimbus.core
  (:require
    [clojure.java.classpath :as cp]
    [clojure.tools.namespace.find :as find]
    [mount.core :as mount :refer [defstate]]
    [trident.util :as u]))

(defn plugins []
  (for [form (find/find-ns-decls (cp/classpath))
        :let [sym (second form)
              {:keys [nimbus]} (meta sym)]
        :when nimbus]
    sym))

(defstate config
  :start (u/map-to
           #(some-> %
              name
              (symbol "config")
              resolve
              deref)
           (plugins)))

(defn -main []
  (mapv require (plugins))
  (mount/start))

(comment
  (do
    (shutdown-agents)
    (System/exit 0)))
