(ns demo-tasks
  (:require [com.biffweb.run :as biff.run]))

(def tasks
  {"a" {:task 'task-a/task-a
        :doc  "Perform task A"}
   "b" {:task 'task-b/task-b
        :doc  "Perform task B"}})

(defn -main [& args]
  (apply biff.run/main* tasks args))
