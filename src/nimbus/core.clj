(ns nimbus.core
  (:require
    [clojure.java.classpath :as cp]
    [clojure.tools.namespace.find :as find]
    [mount.core :as mount]))

(doseq [form (find/find-ns-decls (cp/classpath))
        :let [sym (second form)
              {:keys [nimbus]} (meta sym)]
        :when nimbus]
  (require sym))

(defn -main []
  (mount/start))
