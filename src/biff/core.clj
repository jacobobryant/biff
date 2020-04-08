(ns biff.core
  (:require
    [biff.util :as util]
    [clojure.java.classpath :as cp]
    [clojure.tools.namespace.find :as tn-find]
    [mount.core :as mount :refer [defstate]]
    [trident.util :as u]))

(def debug (boolean (System/getenv "DEBUG")))

(defn plugins []
  (for [form (tn-find/find-ns-decls (cp/classpath))
        :let [sym (second form)
              {:keys [biff]} (meta sym)]
        :when biff]
    sym))

(defstate config
  :start {:plugins (u/map-to
                     #(some-> %
                        name
                        (symbol "config")
                        resolve
                        deref)
                     (plugins))
          :main (:biff/config (util/deps))})

(defn -main []
  (mapv #(u/catchall (require %)) (plugins))
  (mount/start)
  (println "Biff started"))
