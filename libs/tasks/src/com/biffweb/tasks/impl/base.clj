(ns com.biffweb.tasks.impl.base)

(def base-tasks
  {"add"    {:task 'com.biffweb.tasks/add
             :doc  "Add the latest release of a dependency to deps.edn."}
   "format" {:task 'com.biffweb.tasks/format
             :doc  "Format code with cljfmt."}
   "lint"   {:task 'com.biffweb.tasks/lint
             :doc  "Lint code with clj-kondo."}
   "nrepl"  {:task 'com.biffweb.tasks/nrepl
             :doc  "Start an nREPL server."}
   "test"   {:task 'com.biffweb.tasks/test
             :doc  "Run tests with Kaocha."
             :help :invoke}

   "update"
   {:task 'com.biffweb.tasks/update
    :doc  "Update dependencies with antq and update clj-kondo files."}})
