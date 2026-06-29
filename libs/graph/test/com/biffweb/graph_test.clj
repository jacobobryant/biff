(ns com.biffweb.graph-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.biffweb.graph :as graph]))

(graph/defresolver user-by-id
  {:input  [:user/id]
   :output [:user/name :user/email]}
  [_ctx {:user/keys [id]}]
  {:user/name  ({1 "Alice" 2 "Bob"} id)
   :user/email ({1 "alice@example.com" 2 "bob@example.com"} id)})

(graph/defresolver current-user
  {:output [:user/id]}
  [ctx _input]
  {:user/id (:current-user-id ctx)})

(graph/defresolver user-friends
  {:input  [:user/id]
   :output [{:user/friends [:user/id]}]}
  [_ctx {:user/keys [id]}]
  {:user/friends (case id
                   1 [{:user/id 2}]
                   [])})

(graph/defresolver greeting
  {:input  [:user/name]
   :output [:user/greeting]}
  [_ctx {:user/keys [name]}]
  {:user/greeting (str "Hi " name)})

(def ctx
  (graph/new-ctx [user-by-id
                  current-user
                  user-friends
                  greeting]))

(deftest query-with-explicit-ctx-test
  (is (= {:user/name  "Alice"
          :user/email "alice@example.com"}
         (graph/query ctx {:user/id 1} [:user/name :user/email]))))

(deftest query-with-get-ctx-test
  (is (= {:user/id 2 :user/name "Bob"}
         (graph/query {:biff.graph/get-ctx (fn [] ctx)
                       :current-user-id    2}
                      [:user/id :user/name]))))

(deftest nested-query-test
  (is (= {:user/friends [{:user/name "Bob"}]}
         (graph/query ctx {:user/id 1} [{:user/friends [:user/name]}])))
  (is (= {:user/friends []}
         (graph/query ctx {:user/id 2} [{:user/friends [:user/name]}]))))

(deftest nested-input-test
  (let [ctx (graph/new-ctx
             [(graph/resolver
               {:id         :test/nested
                :input      [{:x [:y]}]
                :output     [:z]
                :resolve-fn (fn [_ctx {:keys [x]}]
                              {:z (if (sequential? x)
                                    (mapv :y x)
                                    (:y x))})})])]
    (is (= {:x {:y 1}}
           (graph/query ctx {:x {:y 1 :extra 2}} [{:x [:y]}])))
    (is (= {:x [{:y 1} {:y 2}]}
           (graph/query ctx {:x [{:y 1 :extra 2} {:y 2}]} [{:x [:y]}])))
    (is (= {:z 1}
           (graph/query ctx {:x {:y 1 :extra 2}} [:z])))
    (is (= {:z [1 2]}
           (graph/query ctx {:x [{:y 1 :extra 2} {:y 2}]} [:z])))))

(deftest unresolved-join-test
  (let [calls (atom [])
        ctx   (graph/new-ctx
               [(graph/resolver
                 {:id         :test/x
                  :input      [:missing]
                  :output     [{:x [:y]}]
                  :resolve-fn (fn [_ctx _input]
                                {:x {:y 1}})})
                (graph/resolver
                 {:id         :test/z
                  :output     [:z]
                  :resolve-fn (fn [_ctx _input]
                                (swap! calls conj :z)
                                {:z 1})})])]
    (let [ex (try
               (graph/query ctx [{:x [:y]}])
               nil
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "Could not resolve :missing"
             (ex-message ex)))
      (is (= {:biff.graph/trace [{:resolving :query
                                  :path      [:x]}
                                 {:resolving :test/x
                                  :path      [:missing]}]}
             (ex-data ex))))
    (is (= {:z 1}
           (graph/query ctx [{[:? :x] [:y]} :z])))
    (is (= [:z] @calls))
    (is (thrown-with-msg?
         AssertionError
         #"invalid"
         (graph/query ctx [[:? {:x [:y]}] :z])))
    (reset! calls [])
    (let [ex (try
               (graph/query ctx [{:x [:y]} :z])
               nil
               (catch clojure.lang.ExceptionInfo e
                 e))]
      (is (= "Could not resolve :missing"
             (ex-message ex)))
      (is (= {:biff.graph/trace [{:resolving :query
                                  :path      [:x]}
                                 {:resolving :test/x
                                  :path      [:missing]}]}
             (ex-data ex))))
    (is (= [] @calls))))

(deftest nested-unresolved-trace-test
  (let [ctx (graph/new-ctx
             [(graph/resolver
               {:id         :test/b
                :output     [{:b [:seed]}]
                :resolve-fn (fn [_ctx _input] {})})
              (graph/resolver
               {:id         :test/d
                :input      [:g]
                :output     [{:d [:ok]}]
                :resolve-fn (fn [_ctx _input]
                              {:d {:ok true}})})])
        ex  (try
              (graph/query ctx {:b {:seed true}} [{:b [{:d [:ok]}]}])
              nil
              (catch clojure.lang.ExceptionInfo e
                e))]
    (is (= "Could not resolve :g"
           (ex-message ex)))
    (is (= {:biff.graph/trace [{:resolving :query
                                :path      [:b :d]}
                               {:resolving :test/d
                                :path      [:g]}]}
           (ex-data ex)))))

(deftest invalid-input-shape-test
  (let [ctx (graph/new-ctx
             [(graph/resolver
               {:id         :test/join
                :output     [{:x [:y]}]
                :resolve-fn (fn [_ctx _input] {})})
              (graph/resolver
               {:id         :test/scalar
                :output     [:z]
                :resolve-fn (fn [_ctx _input] {})})])]
    (is (thrown-with-msg?
         AssertionError
         #"Input attr :x is a join but value is a scalar"
         (graph/query ctx {:x 1} [:z])))
    (is (thrown-with-msg?
         AssertionError
         #"Input attr :z is a scalar but value is a join"
         (graph/query ctx {:z {:a 1}} [:z])))
    (is (thrown-with-msg?
         AssertionError
         #"Input attr :z is a scalar but value is a join"
         (graph/query ctx {:x {:z {:a 1}}} [{:x [:y]}])))))

(deftest scalar-hiccup-output-test
  (let [ctx (graph/new-ctx
             [(graph/resolver
               {:id         :test/view
                :output     [:view]
                :resolve-fn (fn [_ctx _input]
                              {:view [:div {:class "notice"} "Hello"]})})])]
    (is (= {:view [:div {:class "notice"} "Hello"]}
           (graph/query ctx [:view])))))

(deftest resolver-exception-test
  (testing "top-level resolver exception"
    (let [ctx (graph/new-ctx
               [(graph/resolver
                 {:id         :test/a
                  :output     [:a]
                  :resolve-fn (fn [_ctx _input]
                                (throw (ex-info "boom" {:x 1})))})])
          ex  (try
                (graph/query ctx [:a])
                nil
                (catch clojure.lang.ExceptionInfo e
                  e))]
      (is (= "Resolver :test/a threw an exception"
             (ex-message ex)))
      (is (= {:biff.graph/trace [{:resolving :query
                                  :path      [:a]}
                                 {:resolving :test/a}]
              :biff.graph/input {}}
             (ex-data ex)))
      (is (= "boom" (ex-message (ex-cause ex))))))
  (testing "nested resolver input exception"
    (let [ctx (graph/new-ctx
               [(graph/resolver
                 {:id         :test/b
                  :output     [{:b [:seed]}]
                  :resolve-fn (fn [_ctx _input] {})})
                (graph/resolver
                 {:id         :test/d
                  :input      [:g]
                  :output     [{:d [:ok]}]
                  :resolve-fn (fn [_ctx _input] {})})
                (graph/resolver
                 {:id         :test/g
                  :output     [:g]
                  :resolve-fn (fn [_ctx _input]
                                (throw (ex-info "nested boom" {})))})])
          ex  (try
                (graph/query ctx {:b {:seed true}} [{:b [{:d [:ok]}]}])
                nil
                (catch clojure.lang.ExceptionInfo e
                  e))]
      (is (= "Resolver :test/g threw an exception"
             (ex-message ex)))
      (is (= {:biff.graph/trace [{:resolving :query
                                  :path      [:b :d]}
                                 {:resolving :test/d
                                  :path      [:g]}
                                 {:resolving :test/g}]
              :biff.graph/input {}}
             (ex-data ex)))
      (is (= "nested boom" (ex-message (ex-cause ex)))))))

(deftest derived-query-test
  (is (= {:user/greeting "Hi Alice"}
         (graph/query ctx {:user/id 1} [:user/greeting]))))

(deftest dynamic-resolver-test
  (let [ctx (graph/new-ctx
             [(graph/resolver
               {:id         :test/dynamic
                :input      [:x]
                :output     [:y]
                :resolve-fn (fn [_ctx {:keys [x]}]
                              {:y (* x 2)})})])]
    (is (= {:y 10}
           (graph/query ctx {:x 5} [:y])))))

(deftest batch-dynamic-resolver-test
  (let [calls (atom 0)
        ctx   (graph/new-ctx
               [(graph/resolver
                 {:id         :test/batch
                  :input      [:x]
                  :output     [:y]
                  :batch      true
                  :resolve-fn (fn [_ctx inputs]
                                (swap! calls inc)
                                (mapv (fn [{:keys [x]}] {:y (* x 2)}) inputs))})])]
    (is (= [{:y 2} {:y 4}]
           (graph/query ctx [{:x 1} {:x 2}] [:y])))
    (is (= 1 @calls))))

(deftest module-test
  (let [modules-var (atom [{:biff.graph/resolvers [user-by-id]}])
        get-ctx     (:biff.graph/get-ctx ((:biff.core/init (graph/module)) modules-var))
        ctx-1       (get-ctx)
        ctx-2       (get-ctx)]
    (is (= {:user/name "Alice"}
           (graph/query {:biff.graph/get-ctx get-ctx} {:user/id 1} [:user/name])))
    (is (identical? ctx-1 ctx-2))
    (swap! modules-var conj {:biff.graph/middleware [(fn [resolver] resolver)]})
    (is (not (identical? ctx-1 (get-ctx))))))

(deftest fx-handler-test
  (is (= {:user/name "Alice"}
         ((:biff.graph.fx/query graph/fx-handlers)
          ctx
          {:user/id 1}
          [:user/name]))))
