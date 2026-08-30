(ns com.biffweb.fx-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [com.biffweb.fx :as biff.fx :refer [defpipeline]])
  (:import [java.time Instant]
           [java.util UUID]))

(deftest machine-runs-effects-across-transitions
  (let [seen    (atom [])
        machine (biff.fx/machine
                 ::transition-test
                 :start
                 (fn [{:keys [from-ctx]}]
                   {:biff.fx/seq  [{:prefix from-ctx}
                                   {:combined [:test/concat "-effect"]}]
                    :biff.fx/next :finish})
                 :finish
                 (fn [ctx {:keys [prefix combined]}]
                   {:biff.fx/return
                    {:prefix   prefix
                     :combined combined
                     :now?     (instance? Instant (:biff.fx/now ctx))
                     :seed?    (integer? (:biff.fx/seed ctx))}}))]
    (is (= {:prefix   "ctx"
            :combined "ctx-effect"
            :now?     true
            :seed?    true}
           (machine {:from-ctx "ctx"

                     :biff.fx/handlers
                     {:test/concat (fn [ctx suffix]
                                     (swap! seen conj
                                            (select-keys ctx
                                                         [:from-ctx :prefix]))
                                     (str (:from-ctx ctx) suffix))}})))
    (is (= [{:from-ctx "ctx"}]
           @seen))))

(deftest machine-with-no-arguments-returns-state-functions
  (let [start   (fn [{:keys [value]}]
                  {:effect [:test/raw value]})
        machine (biff.fx/machine ::raw-state-test :start start)]
    (is (= {:start start} (machine)))
    (is (= {:effect [:test/raw 42]}
           ((:start (machine)) {:value 42})))))

(deftest machine-evaluates-an-initial-effect
  (let [machine (biff.fx/machine
                 ::initial-effect-test
                 [:test/value 2]
                 :start
                 (fn [_ effect-result x y]
                   {:biff.fx/return [effect-result x y]}))]
    (is (= [4 5 6]
           (machine {:biff.fx/handlers
                     {:test/value (fn [_ x] (* 2 x))}}
                    5 6)))
    (is (= {:biff.fx/return [:mock 5 6]}
           ((:start (machine)) {} :mock 5 6)))))

(deftest machine-returns-values-and-evaluates-direct-effects
  (let [value-machine  (biff.fx/machine ::value :start (fn [_] [1 2 3]))
        effect-machine (biff.fx/machine ::effect
                                        :start
                                        (fn [_] [:test/value 3]))]
    (is (= [1 2 3] (value-machine {})))
    (is (= 6 (effect-machine {:biff.fx/handlers
                              {:test/value (fn [_ x] (* 2 x))}})))))

(deftest machine-evaluates-sequenced-effects-before-the-output-map
  (let [calls   (atom [])
        machine (biff.fx/machine
                 ::sequence
                 :start
                 (fn [_]
                   {:a           1
                    :ordinary    [:test/effect :ordinary]
                    :biff.fx/seq [[:test/effect :standalone]
                                  {:a 2 :first [:test/effect :first]}
                                  {:a 3 :second [:test/effect :second]}]}))]
    (is (= {:a        1
            :first    :first
            :second   :second
            :ordinary :ordinary}
           (machine {:biff.fx/handlers
                     {:test/effect (fn [_ value]
                                     (swap! calls conj value)
                                     value)}})))
    (is (= [:standalone :first :second :ordinary] @calls))))

(deftest pipeline-runs-state-functions-in-order
  (let [state-1  (fn [_ x] [:test/double x])
        state-2  (fn [_ result] {:result result :effect [:test/inc result]})
        state-3  (fn [_ {:keys [result effect]}] [result effect])
        pipeline (biff.fx/pipeline
                  ::runs-in-order
                  [state-1 state-2 state-3])
        ctx      {:biff.fx/handlers
                  {:test/double (fn [_ x] (* 2 x))
                   :test/inc    (fn [_ x] (inc x))}}]
    (is (= [state-1 state-2 state-3] (pipeline)))
    (is (= [6 7] (pipeline ctx 3)))))

(deftest pipeline-supports-early-returns
  (let [called?  (atom false)
        pipeline (biff.fx/pipeline
                  ::early-return
                  (fn [_]
                    {:effect         [:test/value]
                     :biff.fx/return :done})
                  (fn [_ _]
                    (reset! called? true)))]
    (is (= :done (pipeline {:biff.fx/handlers
                            {:test/value (constantly :ignored)}})))
    (is (false? @called?))))

(deftest pipeline-supports-initial-effects
  (let [state-fn (fn [_ initial-result x]
                   (+ initial-result x))
        pipeline (biff.fx/pipeline
                  ::initial-effect
                  [:test/value 2]
                  state-fn)]
    (is (= [state-fn] (pipeline)))
    (is (= 7 (pipeline {:biff.fx/handlers
                        {:test/value (fn [_ x] (* 2 x))}}
                       3)))))

(defpipeline defined-pipeline
  (fn [_ x] (inc x))
  (fn [_ x] (* 2 x)))

(deftest defpipeline-defines-a-pipeline
  (is (= 8 (defined-pipeline {} 3))))

(deftest machine-prefers-get-handlers-over-ctx-handlers
  (let [machine (biff.fx/machine
                 ::handler-precedence-test
                 :start
                 (fn [_]
                   {:response [:biff.fx/http {:url "https://example.com"}]}))]
    (is (= {:response :from-get-handlers}
           (machine {:biff.fx/handlers
                     {:biff.fx/http (fn [_ _request] :from-ctx-handlers)}

                     :biff.fx/get-handlers
                     (fn []
                       {:biff.fx/http (fn [_ _request] :from-get-handlers)})})))))

(deftest machine-wraps-handler-errors-with-context
  (let [machine (biff.fx/machine
                 ::handler-error-test
                 :start
                 (fn [_]
                   {:payload  (apply str (repeat 600 "x"))
                    :response [:test/fail "boom"]}))
        ex      (try
                  (machine {:biff.fx/handlers
                            {:test/fail (fn [_ msg]
                                          (throw (ex-info msg {})))}})
                  (catch clojure.lang.ExceptionInfo e
                    e))]
    (is (= "Handler function threw an exception"
           (ex-message ex)))
    (is (= ::handler-error-test
           (:biff.fx/machine-name (ex-data ex))))
    (is (= :start
           (:biff.fx/state (ex-data ex))))
    (is (= []
           (:biff.fx/trace (ex-data ex))))
    (is (= ["boom"]
           (:biff.fx/handler-args (ex-data ex))))
    (is (= 500
           (count (get-in (ex-data ex) [:biff.fx/output :payload]))))
    (is (str/ends-with? (get-in (ex-data ex) [:biff.fx/output :payload])
                        "…"))))

(deftest module-collects-handlers-from-modules
  (let [modules-var (atom [{:biff.fx/handlers
                            {:test/a      identity
                             :test/shared (constantly :first)}}
                           {:biff.fx/handlers
                            {:test/b      str
                             :test/shared (constantly :second)}}])
        init        ((:biff.core/init (biff.fx/module)) modules-var)]
    (is (= #{:test/a :test/b :test/shared}
           (set (keys ((:biff.fx/get-handlers init))))))
    (is (= :second
           ((get ((:biff.fx/get-handlers init)) :test/shared) nil)))
    (swap! modules-var conj
           {:biff.fx/handlers {:test/c      keyword
                               :test/shared (constantly :third)}})
    (is (= #{:test/a :test/b :test/c :test/shared}
           (set (keys ((:biff.fx/get-handlers init))))))
    (is (= :third
           ((get ((:biff.fx/get-handlers init)) :test/shared) nil)))))

(deftest uuid4-is-deterministic-and-rfc-compatible
  (let [[uuid-a next-a] (biff.fx/uuid4 42)
        [uuid-b next-b] (biff.fx/uuid4 42)
        [uuid-c _]      (biff.fx/uuid4 43)]
    (is (instance? UUID uuid-a))
    (is (= uuid-a uuid-b))
    (is (= next-a next-b))
    (is (not= uuid-a uuid-c))
    (is (= 4 (.version uuid-a)))
    (is (= 2 (.variant uuid-a)))))

(deftest uuid7-is-deterministic-and-rfc-compatible
  (let [instant         (Instant/parse "2024-01-02T03:04:05Z")
        [uuid-a next-a] (biff.fx/uuid7 42 instant)
        [uuid-b next-b] (biff.fx/uuid7 42 instant)
        [uuid-c _]      (biff.fx/uuid7 43 instant)]
    (is (instance? UUID uuid-a))
    (is (= uuid-a uuid-b))
    (is (= next-a next-b))
    (is (not= uuid-a uuid-c))
    (is (= 7 (.version uuid-a)))
    (is (= 2 (.variant uuid-a)))))
