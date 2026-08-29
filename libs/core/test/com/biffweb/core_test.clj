(ns com.biffweb.core-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [com.biffweb.core :as biff.core]
            [com.biffweb.core.impl.validation :as impl.v]))

(def ^:private base-registry
  (biff.core/get-registry))

(defn- with-base-registry [f]
  (binding [impl.v/*registry* (atom base-registry)]
    (f)))

(use-fixtures :each with-base-registry)

(deftest registry-and-validation-test
  (is (= base-registry (biff.core/get-registry)))
  (biff.core/register {:foo :string})
  (is (= (assoc base-registry :foo :string)
         (biff.core/get-registry)))
  (testing "valid input is returned unchanged"
    (is (= {:foo "ok"}
           (biff.core/validate {:foo "ok"} :required [:foo])))
    (is (= [{:foo "a"} {:foo "b"}]
           (biff.core/validate [{:foo "a"} {:foo "b"}]))))
  (testing "extra schemas are supported"
    (is (= {:bar 1}
           (biff.core/validate {:bar 1} :extra-schema {:bar :int}))))
  (testing "invalid input throws a helpful error"
    (is (thrown-with-msg? AssertionError
                          #"Missing required key: :foo"
                          (biff.core/validate {} :required [:foo])))
    (is (thrown-with-msg? AssertionError
                          #"invalid: should be a string"
                          (biff.core/validate {:foo 1})))
    (is (thrown-with-msg? AssertionError
                          #"Expected a map, got 1"
                          (biff.core/validate [1]))))
  (testing "validate-with-ex ignores *assert* and throws ExceptionInfo"
    (binding [*assert* false]
      (is (= {:foo "ok"}
             (biff.core/validate-with-ex {:foo "ok"})))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"invalid: should be a string"
                            (biff.core/validate-with-ex {:foo 1}))))))

(deftest secret-delay-test
  (let [secret (biff.core/secret-delay "super-secret")]
    (is (= "#<SecretDelay: redacted>" (str secret)))
    (is (= "#<SecretDelay: redacted>" (pr-str secret)))
    (is (= "super-secret" (force secret)))
    (is (= "super-secret" (secret)))
    (is (= {:biff.core/secret secret}
           (biff.core/validate {:biff.core/secret secret})))
    (is (= {:biff.core/secret "super-secret"}
           (biff.core/validate {:biff.core/secret "super-secret"})))
    (is (thrown? clojure.lang.ArityException
                 (secret :extra-arg)))))

(deftest start-supports-both-arities-and-component-shim-test
  (biff.core/register {:foo :string
                       :bar :int})
  (let [stopped     (atom [])
        modules-var (atom [{:biff.core/init
                            (fn [modules-var]
                              {:foo (str "modules:" (count @modules-var))})}
                           {:biff.core/init (fn [_modules-var]
                                              {:bar 2})}
                           (biff.core/component-shim
                            :test/component-one
                            (fn [ctx]
                              (assoc ctx
                                     :lifecycle :new-style
                                     :biff/stop
                                     [#(swap! stopped conj :new-style)])))
                           (biff.core/component-shim
                            :test/component-two
                            (fn [ctx]
                              (let [stop-fns
                                    [#(swap! stopped conj :legacy-1)
                                     #(swap! stopped conj :legacy-2)]]
                                (-> ctx
                                    (assoc :legacy true)
                                    (assoc :biff/stop stop-fns)))))])
        start-order [:test/component-one :test/component-two]
        system      (biff.core/start {:foo "from-initial"}
                                     modules-var start-order)
        defaulted   (biff.core/start (atom (take 2 @modules-var)) [])]
    (is (= "from-initial" (:foo system)))
    (is (= 2 (:bar system)))
    (is (= :new-style (:lifecycle system)))
    (is (:legacy system))
    (is (nil? (:biff/stop system)))
    (is (fn? (:biff.core/stop-system system)))
    (is (= "modules:2" (:foo defaulted)))
    (is (= 2 (:bar defaulted)))
    (is (fn? (:biff.core/stop-system defaulted)))
    (biff.core/stop system)
    (is (= [:legacy-1 :legacy-2 :new-style] @stopped))))

(deftest start-validates-modules-init-results-and-lifecycle-output-test
  (biff.core/register {:bar :int})
  (is (thrown-with-msg? AssertionError
                        #"Expected a map, got 1"
                        (biff.core/start (atom [1]) [])))
  (is (thrown-with-msg? AssertionError
                        (re-pattern
                         (str "Conflicting keys were returned by multiple "
                              ":biff.core/init functions"))
                        (biff.core/start
                         (atom [{:biff.core/init (fn [_modules-var] {:bar 1})}
                                {:biff.core/init (fn [_modules-var] {:bar 2})}])
                         [])))
  (is (thrown-with-msg? AssertionError
                        #"invalid: should be an integer"
                        (biff.core/start
                         {}
                         (atom [{:biff.core/init (fn [_modules-var]
                                                   {:bar "bad"})}])
                         [])))
  (is (thrown-with-msg? AssertionError
                        #"invalid: should be an integer"
                        (biff.core/start
                         {}
                         (atom [(biff.core/component-shim
                                 :test/component
                                 (fn [ctx]
                                   (assoc ctx :bar "bad")))])
                         [:test/component]))))

(deftest stop-calls-system-stop-function-test
  (let [stopped (atom [])]
    (biff.core/stop
     {:biff.core/stop-system #(swap! stopped conj :stopped)})
    (is (= [:stopped] @stopped))))

(deftest module-ids-use-module-lifecycles-test
  (biff.core/register {:started :keyword})
  (let [stopped (atom nil)
        module  {:biff.core/id    :test/module
                 :biff.core/start #(assoc % :started :yes)
                 :biff.core/stop  #(reset! stopped (:started %))}
        system  (biff.core/start (atom [module]) [:test/module])]
    (is (= :yes (:started system)))
    (biff.core/stop system)
    (is (= :yes @stopped))))

(deftest module-ids-are-validated-before-init-test
  (let [initialized (atom false)
        started     (atom false)]
    (is (thrown-with-msg?
         AssertionError
         #"must set :biff.core/id"
         (biff.core/start
          (atom [{:biff.core/start identity
                  :biff.core/init  (fn [_] (reset! initialized true))}])
          [])))
    (is (false? @initialized))
    (is (thrown-with-msg?
         AssertionError
         #"Missing module IDs from start order"
         (biff.core/start
          (atom [{:biff.core/id    :test/module
                  :biff.core/start identity}])
          [])))
    (is (thrown-with-msg?
         AssertionError
         #"Start order entries must be qualified module IDs"
         (biff.core/start
          (atom [])
          [(fn [ctx]
             (reset! started true)
             ctx)])))
    (is (thrown-with-msg?
         AssertionError
         #"No modules found for IDs in start order"
         (biff.core/start (atom []) [:test/missing])))
    (is (false? @started))))
