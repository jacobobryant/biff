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

(deftest start-supports-both-arities-and-shims-legacy-stop-test
  (biff.core/register {:foo :string
                       :bar :int})
  (let [stopped     (atom [])
        modules-var (atom [{:biff.core/init (fn [modules-var]
                                              {:foo (str "modules:" (count @modules-var))})}
                           {:biff.core/init (fn [_modules-var]
                                              {:bar 2})}])
        components  [(fn [ctx]
                       (-> ctx
                           (assoc :component :new-style)
                           (update :biff.core/stop
                                   conj
                                   #(swap! stopped conj :new-style))))
                     (fn [ctx]
                       (-> ctx
                           (assoc :legacy true)
                           (assoc :biff/stop [#(swap! stopped conj :legacy-1)
                                              #(swap! stopped conj :legacy-2)])))]
        system      (biff.core/start {:foo "from-initial"} modules-var components)
        defaulted   (biff.core/start modules-var [])]
    (is (= "from-initial" (:foo system)))
    (is (= 2 (:bar system)))
    (is (= :new-style (:component system)))
    (is (:legacy system))
    (is (nil? (:biff/stop system)))
    (is (= 3 (count (:biff.core/stop system))))
    (is (= "modules:2" (:foo defaulted)))
    (is (= 2 (:bar defaulted)))
    (is (= [] (:biff.core/stop defaulted)))
    (biff.core/stop system)
    (is (= [:legacy-1 :legacy-2 :new-style] @stopped))))

(deftest start-validates-modules-init-results-and-component-output-test
  (biff.core/register {:bar :int})
  (is (thrown-with-msg? AssertionError
                        #"Expected a map, got 1"
                        (biff.core/start (atom [1]) [])))
  (is (thrown-with-msg? AssertionError
                        #"Conflicting keys were returned by multiple :biff.core/init functions"
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
                         (atom [])
                         [(fn [ctx]
                            (assoc ctx :bar "bad"))]))))

(deftest stop-runs-stop-functions-in-reverse-order-test
  (let [stopped (atom [])]
    (biff.core/stop
     {:biff.core/stop [#(swap! stopped conj :first)
                       #(swap! stopped conj :second)
                       #(swap! stopped conj :third)]})
    (is (= [:third :second :first] @stopped))))
