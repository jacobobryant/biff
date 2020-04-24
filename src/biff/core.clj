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
    [biff.util :as bu]
    [taoensso.timbre]))

(def env (keyword (or (System/getenv "BIFF_ENV") :prod)))
(def debug (not= env :prod))

(defn refresh []
  (mount/stop)
  (tn-repl/refresh :after 'mount.core/start)
  :ready)

(defn find-plugins []
  (for [form (tn-find/find-ns-decls (cp/classpath))
        :let [sym (second form)
              {:keys [biff]} (meta sym)]
        :when biff]
    sym))

(defstate config
  :start (bu/merge-safe
           (bu/get-config env)
           {:plugins (u/map-to
                       #(some-> %
                          name
                          (symbol "config")
                          resolve
                          deref)
                       (find-plugins))}))

(defn -main []
  (nrepl/start-server :port 7888)
  (doseq [p (find-plugins)]
    (try
      (require p)
      (catch Exception e
        (println "Plugin not started:" p)
        (.printStackTrace e))))
  (when debug
    (st/instrument))
  (println "Starting Biff plugins")
  (prn (mount/start)))
