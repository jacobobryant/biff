(ns nimbus.core
  (:require
    [clojure.java.classpath :as cp]
    [clojure.tools.namespace.find :as find]
    [mount.core :as mount :refer [defstate]]
    [trident.util :as u]))

(defstate config
  :start (let [plugins (for [form (find/find-ns-decls (cp/classpath))
                             :let [sym (second form)
                                   {:keys [nimbus]} (meta sym)]
                             :when nimbus]
                         sym)]
           (u/map-to
             #(some-> %
                name
                (symbol "config")
                resolve
                deref)
             plugins)))

(defn -main []
  (mount/start)
  (mapv require (keys config))
  (mount/start))
