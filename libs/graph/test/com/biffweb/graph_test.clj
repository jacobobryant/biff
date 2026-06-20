(ns com.biffweb.graph-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb.graph :as graph]))

(graph/defresolver user-by-id
  {:input [:user/id]
   :output [:user/name :user/email]}
  [_ctx {:user/keys [id]}]
  {:user/name ({1 "Alice" 2 "Bob"} id)
   :user/email ({1 "alice@example.com" 2 "bob@example.com"} id)})

(graph/defresolver current-user
  {:output [:user/id]}
  [ctx _input]
  {:user/id (:current-user-id ctx)})

(graph/defresolver user-friends
  {:input [:user/id]
   :output [{:user/friends [:user/id]}]}
  [_ctx {:user/keys [id]}]
  {:user/friends (case id
                   1 [{:user/id 2}]
                   [])})

(graph/defresolver greeting
  {:input [:user/name]
   :output [:user/greeting]}
  [_ctx {:user/keys [name]}]
  {:user/greeting (str "Hi " name)})

(def env
  (graph/new-env [user-by-id
                  current-user
                  user-friends
                  greeting]))

(deftest query-with-explicit-env-test
  (is (= {:user/name "Alice"
          :user/email "alice@example.com"}
         (graph/query env {:user/id 1} [:user/name :user/email]))))

(deftest query-with-get-env-test
  (is (= {:user/id 2 :user/name "Bob"}
         (graph/query {:biff.graph/get-env (fn [] env)
                       :current-user-id 2}
                      [:user/id :user/name]))))

(deftest nested-query-test
  (is (= {:user/friends [{:user/name "Bob"}]}
         (graph/query env {:user/id 1} [{:user/friends [:user/name]}]))))

(deftest derived-query-test
  (is (= {:user/greeting "Hi Alice"}
         (graph/query env {:user/id 1} [:user/greeting]))))

(deftest dynamic-resolver-test
  (let [env (graph/new-env
             [(graph/resolver
               {:id :test/dynamic
                :input [:x]
                :output [:y]
                :resolve-fn (fn [_ctx {:keys [x]}]
                              {:y (* x 2)})})])]
    (is (= {:y 10}
           (graph/query env {:x 5} [:y])))))

(deftest batch-dynamic-resolver-test
  (let [calls (atom 0)
        env (graph/new-env
             [(graph/resolver
               {:id :test/batch
                :input [:x]
                :output [:y]
                :batch true
                :resolve-fn (fn [_ctx inputs]
                              (swap! calls inc)
                              (mapv (fn [{:keys [x]}] {:y (* x 2)}) inputs))})])]
    (is (= [{:y 2} {:y 4}]
           (graph/query env [{:x 1} {:x 2}] [:y])))
    (is (= 1 @calls))))

(deftest module-test
  (let [modules-var (atom [{:biff.graph/resolvers [user-by-id]}])
        get-env (:biff.graph/get-env ((:biff.core/init (graph/module)) modules-var))
        env-1 (get-env)
        env-2 (get-env)]
    (is (= {:user/name "Alice"}
           (graph/query {:biff.graph/get-env get-env} {:user/id 1} [:user/name])))
    (is (identical? env-1 env-2))
    (swap! modules-var conj {:biff.graph/middleware [(fn [resolver] resolver)]})
    (is (not (identical? env-1 (get-env))))))

(deftest fx-handler-test
  (is (= {:user/name "Alice"}
         ((:biff.graph.fx/query graph/fx-handlers)
          env
          {:user/id 1}
          [:user/name]))))
