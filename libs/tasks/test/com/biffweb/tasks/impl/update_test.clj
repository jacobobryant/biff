(ns com.biffweb.tasks.impl.update-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.biffweb.tasks.impl.lint :as lint]
            [com.biffweb.tasks.impl.update :as update]
            [com.biffweb.tasks.impl.util :as util]))

(deftest update-validates-flags-before-doing-work
  (doseq [[args expected-data]
          [[["--unknown"]
            {:args ["--unknown"] :unknown ["--unknown"]}]
           [["--deps-only" "--clj-kondo-files-only"]
            {:args  ["--deps-only" "--clj-kondo-files-only"]
             :flags #{"--deps-only" "--clj-kondo-files-only"}}]]]
    (let [exception (try
                      (apply update/update args)
                      nil
                      (catch clojure.lang.ExceptionInfo e e))]
      (is (= expected-data (ex-data exception))))))

(deftest update-mode-test
  (testing "clj-kondo-only mode skips dependency updates"
    (let [shell-calls (atom [])]
      (with-redefs [util/read-config
                    (constantly {:biff.tasks/clj-kondo-version "1.2.3"})

                    lint/ensure-clj-kondo-binary! (constantly "kondo")

                    util/shell
                    (fn [& args]
                      (swap! shell-calls conj (vec args)))]
        (update/update "--clj-kondo-files-only"))
      (is (= [["kondo" "--parallel" "--dependencies" "--copy-configs"
               "--lint" (System/getProperty "java.class.path")]]
             @shell-calls))))
  (testing "deps-only mode skips clj-kondo"
    (let [outdated-calls (atom 0)]
      (with-redefs [util/read-config
                    (constantly {:biff.tasks/clj-kondo-version "1.2.3"})

                    util/read-deps-edn (constantly {:deps {}})

                    requiring-resolve
                    (fn [resolved-symbol]
                      (condp = resolved-symbol
                        'antq.api/outdated-deps
                        (fn [& _]
                          (swap! outdated-calls inc)
                          [])
                        'antq.api/upgrade-deps!
                        (fn [& _]
                          (throw (AssertionError. "unexpected upgrade")))))]
        (update/update "--deps-only"))
      (is (= 1 @outdated-calls)))))

(deftest upgradeable-dependencies-exclude-local-roots
  (let [deps    {'one/lib {:mvn/version "1"}
                 'two/lib {:local/root "../two"}}
        aliases {:dev {:extra-deps {'three/lib {:git/sha "abc"}
                                    'four/lib  {:local/root "../four"}}}}]
    (is (= {'one/lib   {:mvn/version "1"}
            'three/lib {:git/sha "abc"}}
           (#'update/upgradeable-deps {:deps deps :aliases aliases})))))
