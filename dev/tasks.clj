(ns tasks
  (:require [com.biffweb.run :as biff.run]
            [com.biffweb.tasks :as biff.tasks]))

(def tasks
  (merge
   biff.tasks/lib-tasks
   {"lint"
    {:task 'tasks.lint/lint
     :doc  "Lints code with clj-kondo and some custom rewrite-clj stuff"}

    "docs"
    {:task 'tasks.docs/docs
     :doc  "Generate API docs for all libraries."}

    "profile-ns"
    {:task 'tasks.profile-ns/profile-ns
     :doc  "Profiles the top-level forms in a namespace."}

    "profile-requires"
    {:task 'tasks.profile-requires/profile-requires
     :doc  "Profiles the time spent requiring a namespace."}

    "publish-all"
    {:task 'tasks.publish-all/publish-all
     :doc  "Publishes all libraries to Clojars."}

    "sync-aliases"
    {:task 'tasks.sync-aliases/sync-aliases
     :doc  "Syncs library run aliases with the root run alias."}

    "sync-deps"
    {:task 'tasks.sync-deps/sync-deps
     :doc  "Syncs versions in deps/deps.edn with the other deps.edn files."}}))

(defn -main [& args]
  (apply biff.run/main tasks args))
