(ns nimbus.core
  (:require
    [clojure.java.classpath :as cp]
    [clojure.tools.namespace.find :as tn-find]
    [mount.core :as mount :refer [defstate]]
    [trident.util :as u]))

(def debug (boolean (System/getenv "DEBUG")))

(defn plugins []
  (for [form (tn-find/find-ns-decls (cp/classpath))
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
  (mapv #(u/catchall (require %)) (plugins))
  (mount/start))
