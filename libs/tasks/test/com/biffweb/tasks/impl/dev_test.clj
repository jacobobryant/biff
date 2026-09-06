(ns com.biffweb.tasks.impl.dev-test
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [com.biffweb.run :as biff.run]
            [com.biffweb.tasks.impl.dev :as dev]
            [com.biffweb.tasks.impl.util :as util]))

(deftest restarts-after-creating-missing-classpath-directories
  (let [attributes (make-array java.nio.file.attribute.FileAttribute 0)
        root       (.toFile (java.nio.file.Files/createTempDirectory
                             "biff-dev-test" attributes))
        path       (.getPath (io/file root "target/resources"))
        calls      (atom [])]
    (try
      (with-redefs [util/all-deps-paths (constantly [path])
                    util/shell-inherit  (fn [& args]
                                          (swap! calls conj (vec args)))]
        (dev/dev)
        (is (.isDirectory (io/file path)))
        (is (true? (util/ensure-paths!))))
      (is (= [["clojure" "-M:run" "dev"]] @calls))
      (finally
        (fs/delete-tree root)))))

(deftest existing-classpath-directories-do-not-restart
  (let [calls (atom [])]
    (with-redefs [util/ensure-paths! (constantly true)
                  util/shell-inherit (fn [& _] (swap! calls conj :restart))
                  biff.run/run-task  (fn [task]
                                       (swap! calls conj task))
                  util/read-config   (fn []
                                       (swap! calls conj :start)
                                       (throw
                                        (ex-info "stop before startup" {})))]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"stop before startup"
                            (dev/dev))))
    (is (= ["init" :start] @calls))))
