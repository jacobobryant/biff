(ns com.biffweb.graph-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [com.biffweb.graph :as biff.graph]))

(biff.graph/defresolver display-name-resolver
  {:input  [:user/first-name :user/last-name]
   :output [:user/display-name]}
  [_ctx {:user/keys [first-name last-name]}]
  {:user/display-name (str first-name " " last-name)})

(defn- resolver [opts]
  (biff.graph/resolver opts))

(defn- test-ctx [resolvers]
  (biff.graph/new-ctx resolvers))

(defn- thrown-data [f]
  (try
    (f)
    nil
    (catch clojure.lang.ExceptionInfo e
      (ex-data e))))

(deftest query->ast-parses-supported-query-forms
  (is (= {:user/id           {:kind :scalar}
          :user/display-name {:kind :scalar :optional true}
          :user/profile      {:kind     :join
                              :children {:profile/bio {:kind :scalar}}}
          :user/settings     {:kind :join :wildcard true}
          :user/posts        {:kind     :join
                              :optional true
                              :children {:post/title {:kind :scalar}}}}
         (biff.graph/query->ast
          [:user/id
           [:? :user/display-name]
           {:user/profile [:profile/bio]}
           {:user/settings [:*]}
           {[:? :user/posts] [:post/title]}]))))

(deftest query-resolves-scalars-joins-vectors-and-filters-output
  (let [ctx (test-ctx
             [(resolver
               {:id         ::user
                :input      [:user/id]
                :output     [:user/first-name
                             :user/last-name
                             {:user/profile [:profile/bio]}
                             {:user/posts [:post/title :post/likes]}]
                :resolve-fn (fn [_ctx {:user/keys [id]}]
                              {:user/first-name "Ada"
                               :user/last-name  (str "Lovelace " id)
                               :user/profile    {:profile/bio    "mathematician"
                                                 :profile/hidden true}
                               :user/posts      [{:post/title "Notes"
                                                  :post/likes 10
                                                  :post/draft true}
                                                 {:post/title "Sketch"
                                                  :post/likes 20}]})})
              display-name-resolver])]
    (is (= {:user/display-name "Ada Lovelace 1"
            :user/profile      {:profile/bio "mathematician"}
            :user/posts        [{:post/title "Notes"}
                                {:post/title "Sketch"}]}
           (biff.graph/query
            ctx
            {:user/id 1}
            [:user/display-name
             {:user/profile [:profile/bio]}
             {:user/posts [:post/title]}])))))

(deftest query-supports-sequential-input-and-batch-resolvers
  (let [calls (atom [])
        ctx   (test-ctx
               [(resolver
                 {:id         ::batch-user
                  :input      [:user/id]
                  :output     [:user/name]
                  :batch      true
                  :resolve-fn (fn [_ctx inputs]
                                (swap! calls conj inputs)
                                (mapv (fn [{:user/keys [id]}]
                                        {:user/name (str "User " id)})
                                      inputs))})])]
    (is (= [{:user/name "User 1"}
            {:user/name "User 2"}
            {:user/name "User 1"}]
           (biff.graph/query
            ctx
            [{:user/id 1} {:user/id 2} {:user/id 1}]
            [:user/name])))
    (is (= [[{:user/id 1} {:user/id 2}]]
           @calls))))

(deftest query-caches-repeated-resolver-calls-within-one-query
  (let [calls (atom 0)
        ctx   (test-ctx
               [(resolver
                 {:id         ::cached-company
                  :output     [{:profile/current-company [:company/id]}
                               {:profile/previous-company [:company/id]}]
                  :resolve-fn (fn [_ctx _input]
                                {:profile/current-company  {:company/id 7}
                                 :profile/previous-company {:company/id 7}})})
                (resolver
                 {:id         ::cached-company-name
                  :input      [:company/id]
                  :output     [:company/name]
                  :resolve-fn (fn [_ctx {:company/keys [id]}]
                                (swap! calls inc)
                                {:company/name (str "Company " id)})})])]
    (is (= {:profile/current-company  {:company/id   7
                                       :company/name "Company 7"}
            :profile/previous-company {:company/id   7
                                       :company/name "Company 7"}}
           (biff.graph/query
            ctx
            [{:profile/current-company [:company/id :company/name]}
             {:profile/previous-company [:company/id :company/name]}])))
    (is (= 1 @calls))))

(deftest optional-input-controls-whether-a-resolver-can-run
  (let [required-calls (atom 0)
        optional-calls (atom 0)
        ctx            (test-ctx
                        [(resolver
                          {:id         ::required-missing
                           :input      [:user/nickname]
                           :output     [:user/required-greeting]
                           :resolve-fn (fn [_ctx input]
                                         (swap! required-calls inc)
                                         {:user/required-greeting
                                          (str "Hi " (:user/nickname input))})})
                         (resolver
                          {:id         ::optional-missing
                           :input      [[:? :user/nickname]]
                           :output     [:user/optional-greeting]
                           :resolve-fn (fn [_ctx input]
                                         (swap! optional-calls inc)
                                         {:user/optional-greeting
                                          (str "Hi " (or (:user/nickname input)
                                                         "there"))})})])]
    (is (= {:user/optional-greeting "Hi there"}
           (biff.graph/query ctx {} [:user/optional-greeting])))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Could not resolve :user/nickname"
                          (biff.graph/query ctx {} [:user/required-greeting])))
    (is (= 0 @required-calls))
    (is (= 1 @optional-calls))))

(deftest query-rejects-unresolvable-and-conflicting-attributes
  (let [ctx (test-ctx
             [(resolver
               {:id         ::shape-source
                :output     [{:user/profile [:profile/bio]}]
                :resolve-fn (fn [_ctx _input]
                              {:user/profile {:profile/bio "bio"}})})])]
    (is (thrown-with-msg? AssertionError
                          #"No resolver declares output for `:user/missing`"
                          (biff.graph/query ctx [:user/missing])))
    (is (thrown-with-msg? AssertionError
                          #"Got conflicting attr shapes for `:user/profile`"
                          (biff.graph/query ctx [:user/profile])))))

(deftest resolver-output-shape-is-enforced
  (let [scalar-ctx (test-ctx
                    [(resolver
                      {:id         ::bad-scalar
                       :output     [:user/profile]
                       :resolve-fn (fn [_ctx _input]
                                     {:user/profile {:profile/bio "bio"}})})])
        join-ctx   (test-ctx
                    [(resolver
                      {:id         ::bad-join
                       :output     [{:user/profile [:profile/bio]}]
                       :resolve-fn (fn [_ctx _input]
                                     {:user/profile "bio"})})])]
    (is (thrown-with-msg? AssertionError
                          #"declared :user/profile as a scalar but value is a join"
                          (biff.graph/query scalar-ctx [:user/profile])))
    (is (thrown-with-msg? AssertionError
                          #"declared :user/profile as a join but value is a scalar"
                          (biff.graph/query join-ctx [{:user/profile [:profile/bio]}])))))

(deftest resolver-exceptions-include-trace-and-input
  (let [ctx  (test-ctx
              [(resolver
                {:id         ::throws
                 :input      [:user/id]
                 :output     [:user/name]
                 :resolve-fn (fn [_ctx _input]
                               (throw (ex-info "boom" {})))})])
        data (thrown-data #(biff.graph/query ctx {:user/id 1} [:user/name]))]
    (is (= [{:resolving :query :path [:user/name]}
            {:resolving ::throws}]
           (:biff.graph/trace data)))
    (is (= {:user/id 1}
           (:biff.graph/input data)))))

(deftest module-builds-context-from-current-modules
  (let [modules-var (atom [{:biff.graph/resolvers
                            [(resolver
                              {:id         ::module-name
                               :input      [:user/id]
                               :output     [:user/name]
                               :resolve-fn (fn [_ctx {:user/keys [id]}]
                                             {:user/name (str "User " id)})})]}])
        ctx         ((:biff.core/init (biff.graph/module)) modules-var)]
    (is (= {:user/name "User 1"}
           (biff.graph/query ctx {:user/id 1} [:user/name])))
    (reset! modules-var
            [{:biff.graph/resolvers
              [(resolver
                {:id         ::module-title
                 :input      [:user/id]
                 :output     [:user/title]
                 :resolve-fn (fn [_ctx {:user/keys [id]}]
                               {:user/title (str "Title " id)})})]}])
    (is (= {:user/title "Title 2"}
           (biff.graph/query ctx {:user/id 2} [:user/title])))))

(deftest resolver-validates-query-forms
  (is (thrown-with-msg? AssertionError
                        #"invalid"
                        (resolver
                         {:id         ::invalid-query
                          :output     [{:too/many [:a] :entries [:b]}]
                          :resolve-fn (fn [_ctx _input] {})})))
  (is (str/includes?
       (ex-message
        (try
          (test-ctx
           [(resolver
             {:id         ::first-shape
              :output     [:user/profile]
              :resolve-fn (fn [_ctx _input] {:user/profile "bio"})})
            (resolver
             {:id         ::second-shape
              :output     [{:user/profile [:profile/bio]}]
              :resolve-fn (fn [_ctx _input]
                            {:user/profile {:profile/bio "bio"}})})])
          (catch AssertionError e
            e)))
       "Got conflicting attr shapes for `:user/profile`")))
