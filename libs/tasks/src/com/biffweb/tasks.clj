(ns com.biffweb.tasks
  "A collection of tasks for Biff projects."
  (:require [com.biffweb.run :as biff.run]))

(def base-tasks
  {"format" {:task 'com.biffweb.tasks.format/format
             :doc  "Format code with cljfmt"}
   "lint"   {:task 'com.biffweb.tasks.lint/lint
             :doc  "Lint code with clj-kondo"}
   "nrepl"  {:task 'com.biffweb.tasks.nrepl/nrepl
             :doc  "Start an nrepl server"}
   "test"   {:task 'com.biffweb.tasks.test/test
             :doc  "Run tests"}
   "update" {:task 'com.biffweb.tasks.update/update
             :doc  "Update dependencies, auto-generated project files, and clj-kondo cache"}})

(def tasks
  (merge base-tasks
         {"css"          {:task 'com.biffweb.tasks.css/css
                          :doc  "Compile CSS with Tailwind"}
          "deploy"       {:task 'com.biffweb.tasks.deploy/deploy
                          :doc  "Deploy to a provisioned server"}
          "dev"          {:task 'com.biffweb.tasks.dev/dev
                          :doc  "Start the app in dev mode"}
          "prod-install" {:task 'com.biffweb.tasks.prod/install
                          :doc  "Provision a server so the app can be deployed to it"}
          "prod-logs"    {:task 'com.biffweb.tasks.prod/logs
                          :doc  "Tail logs from the server"}
          "prod-nrepl"   {:task 'com.biffweb.tasks.prod/nrepl
                          :doc  "Start an SSH tunnel to a remote nREPL server"}
          "prod-restart" {:task 'com.biffweb.tasks.prod/restart
                          :doc  "Restart the app's systemd service on the server"}
          "setup"        {:task 'com.biffweb.tasks.setup/setup
                          :doc  "Run one-time setup tasks for a new project"}
          "uberjar"      {:task 'com.biffweb.tasks.uberjar/uberjar
                          :doc  "Generate an uberjar"}}))

(defn -main [& args]
  (apply biff.run/main* tasks args))
