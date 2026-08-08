(ns com.biffweb.tasks.impl.util-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [com.biffweb.tasks :as tasks]
            [com.biffweb.tasks.impl.util :as util]))

(deftest shell-quote-test
  (is (= "''" (util/shell-quote "")))
  (is (= "'plain text'" (util/shell-quote "plain text")))
  (is (= "'don'\"'\"'t'" (util/shell-quote "don't")))
  (is (= "'42'" (util/shell-quote 42))))

(deftest ssh-target-test
  (is (= "deploy@example.com"
         (util/ssh-target {:biff.tasks/deployment-name "deploy"
                           :biff.tasks/domain          "example.com"}))))

(deftest relative-path-test
  (let [root (io/file (System/getProperty "java.io.tmpdir") "tasks-relative")]
    (is (= "src/example.clj"
           (util/relative-path (io/file root "docs")
                               (io/file root "docs/src/example.clj"))))
    (is (= "../src/example.clj"
           (util/relative-path (io/file root "docs")
                               (io/file root "src/example.clj"))))))

(deftest read-config-test
  (testing "defaults, extra config, selection, and symbolic keys"
    (binding [tasks/*extra-config* {:biff.tasks/domain          "example.com"
                                    :biff.tasks/deployment-name "custom"}]
      (is (= {:biff.tasks/domain          "example.com"
              :biff.tasks/deployment-name "custom"}
             (util/read-config {:required '[domain]
                                :select   '[deployment-name]})))))
  (testing "a missing required value"
    (binding [tasks/*extra-config* {}]
      (let [exception (try
                        (util/read-config {:required '[domain]})
                        nil
                        (catch clojure.lang.ExceptionInfo e e))]
        (is (= "Missing required key: :biff.tasks/domain"
               (ex-message exception)))
        (is (= {:key :biff.tasks/domain} (ex-data exception))))))
  (testing "an invalid value"
    (binding [tasks/*extra-config* {:biff.tasks/nrepl-port "7888"}]
      (let [exception (try
                        (util/read-config)
                        nil
                        (catch clojure.lang.ExceptionInfo e e))]
        (is (= {:key :biff.tasks/nrepl-port :expected "an integer"}
               (ex-data exception)))))))

(deftest dependency-paths-test
  (with-redefs [util/read-deps-edn
                (constantly {:paths   ["src" "resources" "src"]
                             :aliases {:dev  {:extra-paths ["dev" "src"]}
                                       :test {:extra-paths ["test"]}}})]
    (is (= ["src" "resources"] (util/deps-paths)))
    (is (= ["src" "resources" "dev" "test"] (util/all-deps-paths)))))

(deftest clojure-files-test
  (with-redefs [util/project-files
                (constantly (mapv io/file ["a.clj" "b.cljc" "c.cljs"
                                           "d.edn" "e.txt" "clj"]))]
    (is (= ["a.clj" "b.cljc" "c.cljs" "d.edn"]
           (mapv #(.getPath %) (util/clojure-files))))))
