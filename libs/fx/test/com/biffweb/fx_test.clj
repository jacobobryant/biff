(ns com.biffweb.fx-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.biffweb.core :as biff.core]
            [com.biffweb.fx :as fx]
            [com.biffweb.fx.impl :as impl]))

;; === Machine basics ===

(deftest machine-runs-start-and-returns
  (let [m (fx/machine ::basic
                      :start (fn [_] {:status 200 :body "ok"}))]
    (is (= {:status 200 :body "ok"} (m {})))))

(deftest machine-chains-states-with-effects
  (let [m (fx/machine ::chain
                      :start (fn [_] {:db-result [:biff.fx/db :load] :biff.fx/next :second})
                      :second (fn [{:keys [db-result]}] {:result db-result}))]
    (is (= {:result :loaded}
           (m {:biff.fx/handlers
               {:biff.fx/db (fn [_ _] :loaded)}})))))

(deftest machine-runs-effects-and-stores-results
  (let [m (fx/machine ::effects
                      :start (fn [_] {:doubled [:biff.fx/double 5] :biff.fx/next :use-result})
                      :use-result (fn [ctx] {:result (:doubled ctx)}))]
    (is (= {:result 10}
           (m {:biff.fx/handlers
               {:biff.fx/double (fn [_ n] (* 2 n))}})))))

(deftest machine-two-arity-runs-single-state
  (let [m (fx/machine ::two-arity
                      :start (fn [_] {:db-result [:biff.fx/db :q] :biff.fx/next :second})
                      :second (fn [{:keys [x]}] {:result (* x 2)}))]
    (is (= {:result 10} (m {:x 5} :second)))))

(deftest machine-injects-biff-fx-now
  (let [m (fx/machine ::now-test
                      :start (fn [ctx] {:now-class (class (:biff.fx/now ctx))}))]
    (is (= java.time.Instant
           (:now-class (m {}))))))

(deftest machine-injects-seed
  (let [m (fx/machine ::seed-test
                      :start (fn [ctx] {:has-seed (some? (:biff.fx/seed ctx))}))]
    (is (true? (:has-seed (m {}))))))

(deftest machine-injects-results
  (let [m (fx/machine ::results-test
                      :start (fn [_] {:db-result [:biff.fx/db :q] :biff.fx/next :check})
                      :check (fn [ctx] {:has-results (some? (:biff.fx/results ctx))}))]
    (is (true? (:has-results
                (m {:biff.fx/handlers
                    {:biff.fx/db (fn [_ _] :ok)}}))))))

(deftest machine-allows-effectless-transition
  (let [m (fx/machine ::pure
                      :start (fn [_] {:x 1 :biff.fx/next :second})
                      :second (fn [{:keys [x]}] {:result (inc x)}))]
    (is (= {:result 2}
           (m {})))))

(deftest machine-extra-context-ignored-in-output
  (let [m (fx/machine ::extra
                      :start (fn [_] {:hello "there"}))]
    (is (= {:hello "there"}
           (m {:extra "stuff"})))))

(deftest machine-invalid-state-throws
  (let [m (fx/machine ::invalid
                      :start (fn [_] {:biff.fx/next :nonexistent}))]
    (is (thrown-with-msg? Exception #"Exception while running biff.fx machine"
                          (m {})))))

(deftest machine-effect-exception-wraps-context
  (let [m (fx/machine ::fx-err
                      :start (fn [_] {:boom-result [:biff.fx/boom "input"]}))]
    (is (thrown-with-msg? Exception #"Exception while running biff.fx"
                          (m {:biff.fx/handlers
                              {:biff.fx/boom (fn [_ _] (throw (Exception. "boom")))}})))))

(deftest machine-multiple-effects-in-one-state
  (let [m (fx/machine ::multi-fx
                      :start (fn [_] {:a-result [:biff.fx/a 1] :b-result [:biff.fx/b 2] :biff.fx/next :check})
                      :check (fn [ctx] {:a (:a-result ctx) :b (:b-result ctx)}))]
    (is (= {:a 10 :b 20}
           (m {:biff.fx/handlers
               {:biff.fx/a (fn [_ n] (* 10 n))
                :biff.fx/b (fn [_ n] (* 10 n))}})))))

(deftest machine-results-contain-previous-fx-output
  (let [m (fx/machine ::results-check
                      :start (fn [_] {:db-result [:biff.fx/db :query] :biff.fx/next :check})
                      :check (fn [ctx]
                               {:results (:biff.fx/results ctx)}))]
    (is (= {:results [{:db-result :result-val}]}
           (m {:biff.fx/handlers
               {:biff.fx/db (fn [_ _] :result-val)}})))))

(deftest machine-effect-with-multiple-args
  (let [m (fx/machine ::multi-args
                      :start (fn [_] {:sum-result [:biff.fx/add 1 2 3] :biff.fx/next :check})
                      :check (fn [ctx] {:result (:sum-result ctx)}))]
    (is (= {:result 6}
           (m {:biff.fx/handlers
               {:biff.fx/add (fn [_ctx & nums] (apply + nums))}})))))

(deftest machine-non-effect-vector-values-preserved
  (let [m (fx/machine ::vec-val
                      :start (fn [_] {:data [:not-an-effect 1 2 3]}))]
    (is (= {:data [:not-an-effect 1 2 3]}
           (m {})))))

;; === Deterministic randomness ===

(deftest uuid-deterministic
  (let [[a _] (fx/uuid 42)
        [b _] (fx/uuid 42)]
    (is (= a b))))

(deftest uuid-returns-next-seed
  (let [[_ s1] (fx/uuid 42)
        [_ s2] (fx/uuid s1)]
    (is (not= s1 s2))))

(deftest uuid-returns-uuid-type
  (let [[u _] (fx/uuid 42)]
    (is (instance? java.util.UUID u))))

(deftest random-bytes-deterministic
  (let [[a _] (fx/random-bytes 42 16)
        [b _] (fx/random-bytes 42 16)]
    (is (java.util.Arrays/equals ^bytes a ^bytes b))))

(deftest random-bytes-correct-length
  (let [[bs _] (fx/random-bytes 42 32)]
    (is (= 32 (alength ^bytes bs)))))

(deftest random-bytes-returns-next-seed
  (let [[_ s1] (fx/random-bytes 42 16)
        [_ s2] (fx/random-bytes s1 16)]
    (is (not= s1 s2))))

;; === Module ===

(deftest module-aggregates-fx-handlers
  (let [modules-var  (atom [{:biff.fx/handlers {::double (fn [_ctx n] (* 2 n))}}
                            {:biff.fx/handlers {::triple (fn [_ctx n] (* 3 n))}}])
        get-handlers (:biff.fx/get-handlers
                      ((:biff.core/init (fx/module))
                       modules-var))
        handlers-1   (get-handlers)
        handlers-2   (get-handlers)]
    (is (identical? handlers-1 handlers-2))
    (is (= 10 ((get handlers-1 ::double) {} 5)))
    (is (= 15 ((get handlers-1 ::triple) {} 5)))
    (swap! modules-var conj {:biff.fx/handlers {::quadruple (fn [_ctx n] (* 4 n))}})
    (let [handlers-3 (get-handlers)]
      (is (not (identical? handlers-1 handlers-3)))
      (is (= 20 ((get handlers-3 ::quadruple) {} 5))))))

;; === defmachine macro ===

(fx/defmachine test-machine
  :start (fn [{:keys [x]}] {:result (* x 3)}))

(deftest defmachine-creates-var
  (is (fn? test-machine)))

(deftest defmachine-runs-correctly
  (is (= {:result 15} (test-machine {:x 5}))))

(deftest safe-for-url-accepts-valid
  (is (impl/safe-for-url? "hello-world_123"))
  (is (impl/safe-for-url? "a.b.c"))
  (is (impl/safe-for-url? "foo+bar")))

(deftest safe-for-url-rejects-invalid
  (is (not (impl/safe-for-url? "hello world")))
  (is (not (impl/safe-for-url? "foo/bar")))
  (is (not (impl/safe-for-url? ""))))

(deftest route-star-creates-route-vector
  (let [[uri handler-map] (impl/route* "/test" ::test-route
                                       fx/machine
                                       :start (fn [{:keys [request-method]}]
                                                {:biff.fx/next request-method})
                                       :get (fn [_] {:status 200}))]
    (is (= "/test" uri))
    (is (= ::test-route (:name handler-map)))
    (is (fn? (:get handler-map)))
    (is (nil? (:post handler-map)))))

(deftest wrap-hiccup-wraps-vectors
  (let [f (impl/wrap-hiccup (fn [] [:div "hello"]))]
    (is (= {:body [:div "hello"]} (f)))))

(deftest wrap-hiccup-passes-maps
  (let [f (impl/wrap-hiccup (fn [] {:status 200}))]
    (is (= {:status 200} (f)))))

(deftest wrap-result-passes-result-from-ctx
  (let [f (impl/wrap-result (fn [ctx result] {:ctx-keys (keys ctx) :result result}))]
    (is (= {:ctx-keys [:biff.fx/result] :result {:user "data"}}
           (f {:biff.fx/result {:user "data"}})))))

(deftest machine-uses-context-handlers
  (let [m (fx/machine ::context-handlers
                      :start (fn [_] {:result [::custom-double 5] :biff.fx/next :done})
                      :done (fn [{:keys [result]}] {:result result}))]
    (is (= {:result 10}
           (m {:biff.fx/handlers
               {::custom-double (fn [_ctx n] (* 2 n))}})))))

(deftest machine-uses-get-handlers
  (let [m (fx/machine ::get-handlers
                      :start (fn [_] {:result [::custom-double 5] :biff.fx/next :done})
                      :done (fn [{:keys [result]}] {:result result}))]
    (is (= {:result 10}
           (m {:biff.fx/get-handlers
               (fn []
                 {::custom-double (fn [_ctx n] (* 2 n))})})))))

(deftest get-handlers-take-precedence-over-context-handlers
  (let [m (fx/machine ::handler-precedence
                      :start (fn [_] {:result [::custom-double 5] :biff.fx/next :done})
                      :done (fn [{:keys [result]}] {:result result}))]
    (is (= {:result :custom}
           (m {:biff.fx/handlers     {::custom-double (fn [_ctx n] (* 2 n))}
               :biff.fx/get-handlers (fn []
                                       {::custom-double (fn [_ctx _n] :custom)})})))))

(deftest machine-filters-underscore-keys-from-fx-output
  (let [m (fx/machine ::underscore-test
                      :start (fn [_] {:_internal [:biff.fx/load "x"]
                                      :visible   [:biff.fx/load "y"]}))]
    (is (= {:visible "loaded-y"}
           (m {:biff.fx/handlers
               {:biff.fx/load (fn [_ v] (str "loaded-" v))}})))))

(deftest machine-underscore-keys-available-during-transitions
  (let [m (fx/machine ::underscore-ctx-test
                      :start (fn [_] {:_temp        [:biff.fx/load "data"]
                                      :biff.fx/next :use-it})
                      :use-it (fn [{:keys [_temp]}] {:result (str "used-" _temp)}))]
    (is (= {:result "used-loaded"}
           (m {:biff.fx/handlers
               {:biff.fx/load (fn [_ _] "loaded")}})))))

(deftest machine-validates-state-output
  (let [k ::validated]
    (biff.core/register {k 'int?})
    (let [m (fx/machine ::validate-state-output
                        :start (fn [_] {k "bad"}))]
      (is (thrown-with-msg? AssertionError
                            #":com\.biffweb\.fx-test/validated"
                            (m {}))))))

;; === :biff.fx/return ===

(deftest machine-return-key-returns-value
  (let [m (fx/machine ::return-test
                      :start (fn [_] {:biff.fx/return 42 :other "ignored"}))]
    (is (= 42 (m {})))))

(deftest machine-return-key-with-nil-value
  (let [m (fx/machine ::return-nil
                      :start (fn [_] {:biff.fx/return nil}))]
    (is (nil? (m {})))))

(deftest machine-return-key-with-effect
  (testing "biff.fx/return works with effects - returns the effect result"
    (let [m (fx/machine ::return-fx
                        :start (fn [_] {:biff.fx/return [:biff.fx/http {:url "test"}]}))]
      (is (= {:status 200 :body "ok"}
             (m {:biff.fx/handlers
                 {:biff.fx/http (fn [_ _req] {:status 200 :body "ok"})}}))))))

(deftest machine-return-key-multi-state
  (testing "biff.fx/return in final state works"
    (let [m (fx/machine ::return-multi
                        :start (fn [_] {:data [:biff.fx/load "x"] :biff.fx/next :finish})
                        :finish (fn [{:keys [data]}] {:biff.fx/return (str "result: " data)}))]
      (is (= "result: loaded"
             (m {:biff.fx/handlers
                 {:biff.fx/load (fn [_ _] "loaded")}}))))))

(deftest machine-without-return-key-unchanged
  (testing "Without :biff.fx/return, behavior is unchanged"
    (let [m (fx/machine ::no-return
                        :start (fn [_] {:a 1 :b 2}))]
      (is (= {:a 1 :b 2} (m {}))))))
