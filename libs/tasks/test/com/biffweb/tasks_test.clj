(ns com.biffweb.tasks-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.biffweb.tasks :as tasks]))

(def ^:private delegated-tasks
  {'agent-refresh    {:fn   tasks/agent-refresh
                      :impl 'com.biffweb.tasks.impl.agent/agent-refresh
                      :args []}
   'add              {:fn   tasks/add
                      :impl 'com.biffweb.tasks.impl.add/add
                      :args ["example/example"]}
   'app-code-quality {:fn tasks/app-code-quality

                      :impl
                      'com.biffweb.tasks.impl.code-quality/app-code-quality

                      :args []}
   'lib-code-quality {:fn tasks/lib-code-quality

                      :impl
                      'com.biffweb.tasks.impl.code-quality/lib-code-quality

                      :args []}
   'css              {:fn   tasks/css
                      :impl 'com.biffweb.tasks.impl.css/css
                      :args ["--watch=always"]}
   'deploy           {:fn   tasks/deploy
                      :impl 'com.biffweb.tasks.impl.deploy/deploy
                      :args ["--soft"]}
   'dev              {:fn   tasks/dev
                      :impl 'com.biffweb.tasks.impl.dev/dev
                      :args []}
   'docs             {:fn   tasks/docs
                      :impl 'com.biffweb.tasks.impl.docs/docs
                      :args []}
   'format           {:fn   tasks/format
                      :impl 'com.biffweb.tasks.impl.format/format
                      :args []}
   'lint             {:fn   tasks/lint
                      :impl 'com.biffweb.tasks.impl.lint/lint
                      :args []}
   'nrepl            {:fn   tasks/nrepl
                      :impl 'com.biffweb.tasks.impl.nrepl/nrepl
                      :args ["--help"]}
   'prod-logs        {:fn   tasks/prod-logs
                      :impl 'com.biffweb.tasks.impl.prod/prod-logs
                      :args ["20"]}
   'prod-nrepl       {:fn   tasks/prod-nrepl
                      :impl 'com.biffweb.tasks.impl.prod/prod-nrepl
                      :args []}
   'prod-restart     {:fn   tasks/prod-restart
                      :impl 'com.biffweb.tasks.impl.prod/prod-restart
                      :args []}
   'prod-setup       {:fn   tasks/prod-setup
                      :impl 'com.biffweb.tasks.impl.prod/prod-setup
                      :args ["--copy-only"]}
   'publish          {:fn   tasks/publish
                      :impl 'com.biffweb.tasks.impl.publish/publish
                      :args ["--local"]}
   'init             {:fn   tasks/init
                      :impl 'com.biffweb.tasks.impl.init/init
                      :args []}
   'test             {:fn   tasks/test
                      :impl 'com.biffweb.tasks.impl.test/test
                      :args ["--focus" "unit"]}
   'uberjar          {:fn   tasks/uberjar
                      :impl 'com.biffweb.tasks.impl.uberjar/uberjar
                      :args []}
   'update           {:fn   tasks/update
                      :impl 'com.biffweb.tasks.impl.update/update
                      :args ["--deps-only"]}})

(deftest public-functions-delegate-to-implementations
  (doseq [[task-name {task-fn :fn impl :impl args :args}] delegated-tasks]
    (testing (str task-name)
      (let [calls  (atom [])
            result (with-redefs [requiring-resolve
                                 (fn [resolved-symbol]
                                   (is (= impl resolved-symbol))
                                   (fn [& actual-args]
                                     (swap! calls conj (vec actual-args))
                                     ::result))]
                     (apply task-fn args))]
        (is (= ::result result))
        (is (= [args] @calls))))))

(deftest task-collections-expose-the-public-api
  (is (= #{"add" "code-quality" "css" "deploy" "dev" "format" "init"
           "lint" "nrepl" "prod-logs" "prod-nrepl" "prod-restart"
           "prod-setup" "test" "uberjar" "update"}
         (set (keys tasks/app-tasks))))
  (is (= #{"add" "code-quality" "docs" "format" "lint" "nrepl"
           "publish" "test" "update"}
         (set (keys tasks/lib-tasks))))
  (doseq [task (concat (vals tasks/app-tasks) (vals tasks/lib-tasks))]
    (is (qualified-symbol? (:task task)))
    (is (string? (:doc task)))))

(deftest extra-config-defaults-to-empty
  (is (= {} tasks/*extra-config*)))
