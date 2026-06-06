(ns tasks
  (:require [com.biffweb.run :as biff.run]
            [com.biffweb.tasks.lib :as biff.tasks.lib]))

(def tasks
  (merge biff.tasks.lib/tasks
         {"sync-deps" {:task 'tasks.sync-deps/sync-deps
                       :doc  "Syncs versions in deps/deps.edn with the other deps.edn files."}}))

(defn -main [& args]
  (apply biff.run/main* tasks args))
