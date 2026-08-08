(ns tasks
  (:require [com.biffweb.run :as biff.run]))

(def tasks
  {"a" {:task 'tasks.a/task-a
        :doc  "Perform task A"}
   "b" {:task 'tasks.b/task-b
        :doc  "Perform task B"}})

(defn -main [& args]
  (apply biff.run/main tasks args))
