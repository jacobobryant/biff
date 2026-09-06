(ns tasks
  (:require [com.biffweb.run :as biff.run]
            [com.biffweb.tasks :as biff.tasks]))

(def tasks
  (assoc biff.tasks/lib-tasks
         "css"
         {:task 'com.biffweb.tasks/css
          :doc  "Compile the admin library's CSS with Tailwind."}))

(defn -main [& args]
  (apply biff.run/main tasks args))
