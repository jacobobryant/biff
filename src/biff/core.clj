(ns biff.core
  (:require
    [clojure.edn :as edn]
    [clojure.java.classpath :as cp]
    [clojure.tools.namespace.find :as tn-find]
    [mount.core :as mount :refer [defstate]]
    [orchestra.spec.test :as st]
    [nrepl.server :as nrepl]
    [clojure.tools.namespace.repl :as tn-repl]
    [trident.util :as u]
    [taoensso.timbre]))

(def debug (boolean (System/getenv "DEBUG")))

(defn refresh []
  (mount/stop)
  (tn-repl/refresh :after 'mount.core/start)
  :ready)

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
          :main (-> "deps.edn"
                  slurp
                  edn/read-string
                  :biff/config)})

(defn -main []
  (nrepl/start-server :port 7888)
  (doseq [p (plugins)]
    (try
      (require p)
      (catch Exception e
        (println "Plugin not started:" p)
        (.printStackTrace e))))
  (when debug
    (st/instrument))
  (println "Starting Biff plugins")
  (u/pprint
    (mount/start)))
