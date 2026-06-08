(ns com.biffweb.fx-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [com.biffweb.fx :as biff.fx])
  (:import [java.time Instant]
           [java.util UUID]))

(deftest machine-runs-effects-across-transitions
  (let [seen    (atom [])
        machine (biff.fx/machine
                 ::transition-test
                 :start
                 (fn [{:keys [from-ctx]}]
                   [{:prefix from-ctx}
                    {:combined     [:test/concat "-effect"]
                     :biff.fx/next :finish}])
                 :finish
                 (fn [{:keys [prefix combined] :as ctx}]
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
                                     (swap! seen conj (select-keys ctx [:from-ctx :prefix]))
                                     (str (:prefix ctx) suffix))}})))
    (is (= [{:from-ctx "ctx" :prefix "ctx"}]
           @seen))))

(deftest machine-two-arity-runs-raw-state-function
  (let [machine (biff.fx/machine
                 ::raw-state-test
                 :start
                 (fn [{:keys [value]}]
                   {:effect [:test/raw value]}))]
    (is (= {:effect [:test/raw 42]}
           (machine {:value 42} :start)))))

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
  (let [modules-var (atom [{:biff.fx/handlers {:test/a      identity
                                               :test/shared (constantly :first)}}
                           {:biff.fx/handlers {:test/b      str
                                               :test/shared (constantly :second)}}])
        init        ((:biff.core/init (biff.fx/module)) modules-var)]
    (is (= #{:test/a :test/b :test/shared}
           (set (keys ((:biff.fx/get-handlers init))))))
    (is (= :second
           ((get ((:biff.fx/get-handlers init)) :test/shared) nil)))
    (swap! modules-var conj {:biff.fx/handlers {:test/c      keyword
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
