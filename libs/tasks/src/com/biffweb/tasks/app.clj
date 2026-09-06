(ns com.biffweb.tasks.app
  (:require [com.biffweb.run :as biff.run]
            [com.biffweb.tasks.impl.base :as base]))

(def tasks
  (merge
   base/base-tasks
   {"css"    {:task 'com.biffweb.tasks/css
              :doc  "Compile CSS with Tailwind."}
    "deploy" {:task 'com.biffweb.tasks/deploy
              :doc  "Deploy to a server provisioned with the prod-setup task."}
    "dev"    {:task 'com.biffweb.tasks/dev
              :doc  "Start the app in dev mode."}

    "prod-setup"
    {:task 'com.biffweb.tasks/prod-setup
     :doc  "Provision a server so the app can be deployed to it."}

    "prod-logs"    {:task 'com.biffweb.tasks/prod-logs
                    :doc  "Tail logs from the server."}
    "prod-nrepl"   {:task 'com.biffweb.tasks/prod-nrepl
                    :doc  "Start an SSH tunnel to the production nREPL server."}
    "prod-restart" {:task 'com.biffweb.tasks/prod-restart
                    :doc  "Restart the application in production."}
    "init"         {:task 'com.biffweb.tasks/init
                    :doc  "Initialize a freshly cloned project."}
    "uberjar"      {:task 'com.biffweb.tasks/uberjar
                    :doc  "Generate an uberjar."}
    "code-quality" {:task 'com.biffweb.tasks/app-code-quality
                    :doc  "Format, lint, and test code."}}))

(defn -main [& args]
  (apply biff.run/main tasks args))
