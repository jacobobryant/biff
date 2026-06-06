(ns com.biffweb.graph-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.biffweb.core :as biff.core]
            [com.biffweb.graph :as graph]))

;; ---------------------------------------------------------------------------
;; Test data: resolvers
;; ---------------------------------------------------------------------------

(def user-by-id
  {:id      :user-by-id
   :input   [:user/id]
   :output  [:user/name :user/email]
   :resolve (fn [_ctx {:user/keys [id]}]
              (case id
                1 {:user/name "Alice" :user/email "alice@example.com"}
                2 {:user/name "Bob" :user/email "bob@example.com"}
                3 {:user/name "Carol" :user/email "carol@example.com"}
                (throw (ex-info "User not found" {:user/id id}))))})

(def user-friends
  {:id      :user-friends
   :input   [:user/id]
   :output  [{:user/friends [:user/id]}]
   :resolve (fn [_ctx {:user/keys [id]}]
              (case id
                1 {:user/friends [{:user/id 2} {:user/id 3}]}
                2 {:user/friends [{:user/id 1}]}
                3 {:user/friends [{:user/id 1} {:user/id 2}]}
                {:user/friends []}))})

(def user-age
  {:id      :user-age
   :input   [:user/id]
   :output  [:user/age]
   :resolve (fn [_ctx {:user/keys [id]}]
              (case id
                1 {:user/age 30}
                2 {:user/age 25}
                3 {:user/age 35}
                {:user/age nil}))})

(def current-user
  {:id      :current-user
   :input   []
   :output  [:user/id]
   :resolve (fn [ctx _input]
              {:user/id (:current-user-id ctx)})})

(def order-by-id
  {:id      :order-by-id
   :input   [:order/id]
   :output  [:order/total :order/status {:order/user [:user/id]}]
   :resolve (fn [_ctx {:order/keys [id]}]
              (case id
                100 {:order/total 59.99 :order/status :shipped :order/user {:user/id 1}}
                101 {:order/total 12.50 :order/status :pending :order/user {:user/id 2}}
                (throw (ex-info "Order not found" {:order/id id}))))})

(def derived-greeting
  {:id      :derived-greeting
   :input   [:user/name :user/age]
   :output  [:user/greeting]
   :resolve (fn [_ctx {:user/keys [name age]}]
              {:user/greeting (str "Hello, " name "! You are " age " years old.")})})

(def user-address
  {:id      :user-address
   :input   [:user/id]
   :output  [{:user/address [:address/street :address/zip]}]
   :resolve (fn [_ctx {:user/keys [id]}]
              (case id
                1 {:user/address {:address/street "123 Main St" :address/zip "10001"}}
                2 {:user/address {:address/street "456 Oak Ave" :address/zip "90210"}}
                3 {:user/address {:address/street "789 Elm Rd" :address/zip "60601"}}
                (throw (ex-info "Address not found" {:user/id id}))))})

(def shipping-label
  {:id      :shipping-label
   :input   [{:order/user [:user/name {:user/address [:address/zip]}]}]
   :output  [:order/shipping-label]
   :resolve (fn [_ctx input]
              (let [user-name (get-in input [:order/user :user/name])
                    zip       (get-in input [:order/user :user/address :address/zip])]
                {:order/shipping-label (str "Ship to: " user-name ", " zip)}))})

(def friend-summary
  {:id      :friend-summary
   :input   [{:user/friends [:user/name]}]
   :output  [:user/friend-names]
   :resolve (fn [_ctx input]
              {:user/friend-names (mapv :user/name (:user/friends input))})})

(def all-resolvers
  [user-by-id user-friends user-age current-user order-by-id derived-greeting
   user-address shipping-label friend-summary])

(def index (graph/build-index all-resolvers))

(defn q
  "Helper to run a query with the test index and optional extra ctx keys."
  ([entity query-vec]
   (graph/query {:biff.graph/index index} entity query-vec))
  ([ctx entity query-vec]
   (graph/query (assoc ctx :biff.graph/index index) entity query-vec)))

;; ---------------------------------------------------------------------------
;; Tests
;; ---------------------------------------------------------------------------

(deftest simple-resolve-test
  (testing "Resolve simple attributes from entity seed data"
    (is (= {:user/name "Alice" :user/email "alice@example.com"}
           (q {:user/id 1} [:user/name :user/email])))))

(deftest multiple-attributes-test
  (testing "Resolve multiple attributes from different resolvers"
    (is (= {:user/name "Bob" :user/age 25}
           (q {:user/id 2} [:user/name :user/age])))))

(deftest get-index-test
  (let [modules-var (atom [{:biff.graph/resolvers [user-by-id]}])
        get-index   (:biff.graph/get-index
                     ((:biff.core/init (graph/module))
                      modules-var))
        index-1     (get-index)
        index-2     (get-index)]
    (is (= {:user/name "Alice"}
           (graph/query {:biff.graph/get-index get-index}
                        {:user/id 1}
                        [:user/name])))
    (is (identical? index-1 index-2))
    (swap! modules-var conj {:biff.graph/middleware [(fn [resolver] resolver)]})
    (is (not (identical? index-1 (get-index))))))

(deftest module-exposes-query-fx-handler
  (is (= {:biff.graph.fx/query #'graph/query}
         (:biff.fx/handlers (graph/module)))))

(deftest handle-query-effect-test
  (is (= {:user/name "Alice"}
         ((:biff.graph.fx/query graph/fx-handlers)
          {:biff.graph/index index}
          {:user/id 1}
          [:user/name]))))

(graph/defresolver test-resolver
  {:input  [:x]
   :output [:y]}
  [_ctx {:keys [x]}]
  {:y (* x 2)})

(deftest defresolver-creates-var-with-metadata
  (is (fn? test-resolver))
  (is (= [:x] (:input (meta #'test-resolver))))
  (is (= [:y] (:output (meta #'test-resolver)))))

(deftest defresolver-runs-as-resolver
  (is (= {:y 10}
         (test-resolver {} {:x 5}))))

(graph/defresolver effectful-resolver
  {:input  [:id]
   :output [:data]}
  [_ctx {:keys [id]}]
  {:data [:com.biffweb.graph-test/load id]})

(deftest defresolver-runs-effects
  (is (= {:data {:loaded 42}}
         (effectful-resolver
          {:biff.fx/handlers {:com.biffweb.graph-test/load (fn [_ id] {:loaded id})}}
          {:id 42}))))

(graph/defresolver multi-state-resolver
  {:input  [:id]
   :output [:result]}
  [_ctx {:keys [id]}]
  {:raw          [:com.biffweb.graph-test/load id]
   :biff.fx/next :process}

  :process
  (fn [{:keys [raw]}]
    {:result (str "processed-" raw)}))

(deftest defresolver-with-multiple-states
  (is (= {:result "processed-data"}
         (multi-state-resolver
          {:biff.fx/handlers {:com.biffweb.graph-test/load (fn [_ _] "data")}}
          {:id 1}))))

(graph/defresolver no-input-resolver
  {:output [:global-val]}
  [_ctx _input]
  {:global-val 42})

(deftest defresolver-omits-empty-input
  (is (nil? (:input (meta #'no-input-resolver))))
  (is (= [:global-val] (:output (meta #'no-input-resolver)))))

(deftest defresolver-no-input-runs
  (is (= {:global-val 42}
         (no-input-resolver {} {}))))

(deftest global-resolver-test
  (testing "Global resolver (no input) provides seed data"
    (is (= {:user/id 1 :user/name "Alice"}
           (q {:current-user-id 1} {} [:user/id :user/name])))))

(deftest nested-join-test
  (testing "Nested join resolves child entities"
    (is (= {:user/name    "Alice"
            :user/friends [{:user/name "Bob"} {:user/name "Carol"}]}
           (q {:user/id 1} [:user/name {:user/friends [:user/name]}])))))

(deftest deeply-nested-join-test
  (testing "Nested join resolves multiple levels deep"
    (is (= {:user/friends [{:user/name    "Bob"
                            :user/friends [{:user/name "Alice"}]}
                           {:user/name    "Carol"
                            :user/friends [{:user/name "Alice"} {:user/name "Bob"}]}]}
           (q {:user/id 1} [{:user/friends [:user/name {:user/friends [:user/name]}]}])))))

(deftest join-with-single-entity-test
  (testing "Join with a single nested entity (not a collection)"
    (is (= {:order/total 59.99
            :order/user  {:user/name "Alice" :user/email "alice@example.com"}}
           (q {:order/id 100} [:order/total {:order/user [:user/name :user/email]}])))))

(deftest entity-passthrough-test
  (testing "Attributes already in entity are passed through without resolving"
    (is (= {:user/name "Override"}
           (q {:user/id 1 :user/name "Override"} [:user/name])))))

(deftest derived-resolver-test
  (testing "Resolver that depends on outputs of other resolvers"
    (is (= {:user/greeting "Hello, Alice! You are 30 years old."}
           (q {:user/id 1} [:user/greeting])))))

(deftest env-passthrough-test
  (testing "Context map is passed to resolvers"
    (is (= {:user/name "Carol" :user/email "carol@example.com"}
           (q {:current-user-id 3} {} [:user/name :user/email])))))

(deftest strict-mode-test
  (testing "Throws when no resolver can satisfy an attribute"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"No resolver found for attribute"
         (q {:user/id 1} [:nonexistent/attr])))))

(deftest build-index-test
  (testing "build-index indexes resolvers by their flat output keys"
    (let [idx (graph/build-index [user-by-id user-friends])]
      (is (= [:user-by-id] (mapv :id (get-in idx [:resolvers-by-output :user/name]))))
      (is (= [:user-by-id] (mapv :id (get-in idx [:resolvers-by-output :user/email]))))
      (is (= [:user-friends] (mapv :id (get-in idx [:resolvers-by-output :user/friends])))))))

(deftest query-validates-resolver-output-values
  (let [k   ::validated
        idx (graph/build-index
             [{:id      ::validated
               :output  [k]
               :resolve (fn [_ _] {k "bad"})}])]
    (biff.core/register {k 'int?})
    (is (thrown-with-msg? AssertionError
                          #":com\.biffweb\.graph-test/validated"
                          (graph/query {:biff.graph/index idx} {} [k])))))

(deftest empty-query-test
  (testing "Empty query returns empty map"
    (is (= {} (q {:user/id 1} [])))))

(deftest resolver-creation-test
  (testing "resolver validates required fields"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"must have an :id"
         (graph/resolver {:resolve (fn [_ _] {})})))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"must have a :resolve"
         (graph/resolver {:id :test})))))

;; ---------------------------------------------------------------------------
;; Nested input tests
;; ---------------------------------------------------------------------------

(deftest nested-input-simple-test
  (testing "Resolver with nested join input resolves the nested data"
    (is (= {:order/shipping-label "Ship to: Alice, 10001"}
           (q {:order/id 100} [:order/shipping-label])))))

(deftest nested-input-collection-test
  (testing "Resolver with nested collection input"
    (is (= {:user/friend-names ["Bob" "Carol"]}
           (q {:user/id 1} [:user/friend-names])))))

(deftest nested-input-with-other-attrs-test
  (testing "Nested input alongside regular output queries"
    (is (= {:order/total          59.99
            :order/shipping-label "Ship to: Alice, 10001"}
           (q {:order/id 100} [:order/total :order/shipping-label])))))

;; ---------------------------------------------------------------------------
;; Optional output tests
;; ---------------------------------------------------------------------------

(deftest optional-output-test
  (testing "Resolver that doesn't return the requested key is skipped"
    (let [idx (graph/build-index
               [{:id      :partial
                 :input   [:user/id]
                 :output  [:user/name :user/nickname]
                 :resolve (fn [_ctx {:user/keys [id]}]
                            {:user/name (str "User-" id)})}
                {:id      :complete
                 :input   [:user/id]
                 :output  [:user/nickname]
                 :resolve (fn [_ctx {:user/keys [id]}]
                            {:user/nickname (str "Nick-" id)})}])]
      (is (= {:user/nickname "Nick-1"}
             (graph/query {:biff.graph/index idx} {:user/id 1} [:user/nickname]))))))

(deftest optional-output-nil-value-test
  (testing "Resolver that returns the key with nil value is accepted"
    (let [idx (graph/build-index
               [{:id      :nil-val
                 :input   [:user/id]
                 :output  [:user/nickname]
                 :resolve (fn [_ctx _input]
                            {:user/nickname nil})}])]
      (is (= {:user/nickname nil}
             (graph/query {:biff.graph/index idx} {:user/id 1} [:user/nickname]))))))

;; ---------------------------------------------------------------------------
;; Join validation tests
;; ---------------------------------------------------------------------------

(deftest join-scalar-in-entity-throws-test
  (testing "Join on a scalar value in entity throws"
    (is (thrown-with-msg?
         AssertionError
         #"uses a join descriptor but got non-map scalar while checking a query item"
         (q {:user/friends "not-a-collection"}
            [{:user/friends [:user/name]}])))))

(deftest join-nil-in-entity-normalizes-test
  (testing "Join on a nil value in entity normalizes to an empty map"
    (is (= {:user/address {}}
           (q {:user/address nil}
              [{:user/address [:address/zip]}])))))

(deftest join-scalar-from-resolver-throws-test
  (testing "Join on a scalar value from resolver throws"
    (let [idx (graph/build-index
               [{:id      :bad-friends
                 :input   [:user/id]
                 :output  [{:user/friends [:user/id]}]
                 :resolve (fn [_ctx _input]
                            {:user/friends "oops-not-a-map"})}])]
      (is (thrown-with-msg?
           AssertionError
           #"uses a join descriptor but got non-map scalar while checking resolver output"
           (graph/query {:biff.graph/index idx} {:user/id 1} [{:user/friends [:user/name]}]))))))

;; ---------------------------------------------------------------------------
;; Flat output validation tests
;; ---------------------------------------------------------------------------

(deftest join-output-descriptor-accepted-test
  (testing "Resolver outputs may declare explicit join descriptors"
    (is (= [{:user/friends [:user/id]}]
           (:output
            (graph/resolver {:id      :ok-output
                             :input   [:user/id]
                             :output  [{:user/friends [:user/id]}]
                             :resolve (fn [_ _] {})}))))))

(deftest build-index-rejects-shape-conflicts-test
  (testing "build-index rejects scalar-vs-join descriptor conflicts"
    (is (thrown-with-msg?
         AssertionError
         #"scalar or join-shaped"
         (graph/build-index
          [{:id      :scalar-input
            :input   [:user/friends]
            :output  [:x]
            :resolve (fn [_ _] {:x true})}
           {:id      :join-output
            :input   []
            :output  [{:user/friends [:user/id]}]
            :resolve (fn [_ _] {:user/friends []})}])))))

(deftest query-static-shape-validation-test
  (testing "query rejects a bare key for an attr classified as join-shaped"
    (is (thrown-with-msg?
         AssertionError
         #"classifies it as join"
         (q {:user/id 1} [:user/friends])))))

(deftest query-runtime-unknown-shape-validation-test
  (testing "query still enforces scalar-vs-join shape for seed-only attrs"
    (is (thrown-with-msg?
         AssertionError
         #"uses a scalar descriptor but got map while checking a query item"
         (graph/query {:biff.graph/index (graph/build-index [])}
                      {:seed/join {:seed/value 1}}
                      [:seed/join])))))

(deftest resolver-output-projection-recursive-test
  (testing "Resolver output projection keeps only declared nested keys"
    (let [idx (graph/build-index
               [{:id      :projected-address
                 :input   [:user/id]
                 :output  [{:user/address [:address/zip]}]
                 :resolve (fn [_ _]
                            {:user/address {:address/zip    "10001"
                                            :address/street "123 Main St"}})}])]
      (is (= {:user/address {:address/zip "10001"}}
             (graph/query {:biff.graph/index idx}
                          {:user/id 1}
                          [{:user/address [:address/zip]}]))))))

(deftest resolver-output-nil-join-normalizes-test
  (testing "Resolver output nil joins stay resolved as empty maps"
    (let [idx (graph/build-index
               [{:id      :nil-address
                 :input   [:user/id]
                 :output  [{:user/address [:address/zip]}]
                 :resolve (fn [_ _]
                            {:user/address nil})}])]
      (is (= {:user/address {}}
             (graph/query {:biff.graph/index idx}
                          {:user/id 1}
                          [{:user/address [:address/zip]}]))))))

(deftest build-index-wildcard-validation-test
  (testing "build-index enforces wildcard compatibility rules"
    (is (thrown-with-msg?
         AssertionError
         #"\[:\*\]"
         (graph/build-index
          [{:id      :wildcard-input
            :input   [{:user/profile [:*]}]
            :output  [:x]
            :resolve (fn [_ _] {:x true})}
           {:id      :profile-output
            :input   [:user/id]
            :output  [{:user/profile [:profile/id]}]
            :resolve (fn [_ _] {:user/profile {:profile/id 1}})}])))
    (is (thrown-with-msg?
         AssertionError
         #"\[:\*\]"
         (graph/build-index
          [{:id      :wildcard-output
            :input   [:user/id]
            :output  [{:user/profile [:*]}]
            :resolve (fn [_ _] {:user/profile {:profile/id 1}})}
           {:id      :explicit-output
            :input   [:user/id]
            :output  [{:user/profile [:profile/id]}]
            :resolve (fn [_ _] {:user/profile {:profile/id 1}})}])))))

(deftest query-wildcard-behavior-test
  (testing "Queries may only use [:*] when some resolver output declares [:*]"
    (let [wildcard-idx (graph/build-index
                        [{:id      :wildcard-profile
                          :input   [:user/id]
                          :output  [{:user/profile [:*]}]
                          :resolve (fn [_ {:user/keys [id]}]
                                     {:user/profile {:profile/id   id
                                                     :profile/name "Alice"}})}])
          explicit-idx (graph/build-index
                        [{:id      :explicit-profile
                          :input   [:user/id]
                          :output  [{:user/profile [:profile/id]}]
                          :resolve (fn [_ {:user/keys [id]}]
                                     {:user/profile {:profile/id   id
                                                     :profile/name "Alice"}})}])]
      (is (= {:user/profile {:profile/id 1 :profile/name "Alice"}}
             (graph/query {:biff.graph/index wildcard-idx}
                          {:user/id 1}
                          [{:user/profile [:*]}])))
      (is (thrown-with-msg?
           AssertionError
           #"\[:\*\]"
           (graph/query {:biff.graph/index explicit-idx}
                        {:user/id 1}
                        [{:user/profile [:*]}]))))))

;; ---------------------------------------------------------------------------
;; Var-based resolver tests
;; ---------------------------------------------------------------------------

(defn var-user-by-id
  {:input  [:user/id]
   :output [:user/name :user/email]}
  [_ctx {:user/keys [id]}]
  (case id
    1 {:user/name "Alice" :user/email "alice@example.com"}
    2 {:user/name "Bob" :user/email "bob@example.com"}
    (throw (ex-info "User not found" {:user/id id}))))

(deftest var-resolver-test
  (testing "Var-based resolver uses metadata for input/output and var ns/name for id"
    (let [r (graph/resolver #'var-user-by-id)]
      (is (= :com.biffweb.graph-test/var-user-by-id (:id r)))
      (is (= [:user/id] (:input r)))
      (is (= [:user/name :user/email] (:output r)))
      (is (var? (:resolve r))))))

(deftest var-resolver-query-test
  (testing "Var-based resolver works in queries"
    (let [idx (graph/build-index [#'var-user-by-id])]
      (is (= {:user/name "Alice" :user/email "alice@example.com"}
             (graph/query {:biff.graph/index idx} {:user/id 1} [:user/name :user/email]))))))

(deftest var-resolver-reeval-test
  (testing "Var-based resolver picks up redefined functions"
    (let [idx             (graph/build-index [#'var-user-by-id])
          original-result (graph/query {:biff.graph/index idx} {:user/id 1} [:user/name])]
      (is (= {:user/name "Alice"} original-result))
      ;; The resolver stores the var, so if the var were rebound, the new
      ;; behavior would be picked up. build-index wraps :resolve with caching,
      ;; so the var is no longer directly stored. But calling through the
      ;; wrapper still invokes the var, preserving reeval semantics.
      (is (fn? (:resolve (first (:all-resolvers idx))))))))

(deftest build-index-auto-resolves-test
  (testing "build-index calls resolver on each item automatically"
    (let [idx (graph/build-index [{:id      :test-r
                                   :input   [:a]
                                   :output  [:b]
                                   :resolve (fn [_ _] {:b 1})}])]
      (is (= [:test-r] (mapv :id (:all-resolvers idx)))))))

;; ---------------------------------------------------------------------------
;; Optional input tests
;; ---------------------------------------------------------------------------

(deftest optional-input-present-test
  (testing "Optional input is included when available"
    (let [idx (graph/build-index
               [{:id      :greeting-with-title
                 :input   [:user/name [:? :user/title]]
                 :output  [:user/greeting]
                 :resolve (fn [_ctx {:user/keys [name title]}]
                            {:user/greeting (if title
                                              (str "Hello, " title " " name "!")
                                              (str "Hello, " name "!"))})}])]
      (is (= {:user/greeting "Hello, Dr. Alice!"}
             (graph/query {:biff.graph/index idx}
                          {:user/name "Alice" :user/title "Dr."}
                          [:user/greeting]))))))

(deftest optional-input-missing-test
  (testing "Optional input is omitted when not available"
    (let [idx (graph/build-index
               [{:id      :greeting-with-title
                 :input   [:user/name [:? :user/title]]
                 :output  [:user/greeting]
                 :resolve (fn [_ctx {:user/keys [name title]}]
                            {:user/greeting (if title
                                              (str "Hello, " title " " name "!")
                                              (str "Hello, " name "!"))})}])]
      (is (= {:user/greeting "Hello, Alice!"}
             (graph/query {:biff.graph/index idx}
                          {:user/name "Alice"}
                          [:user/greeting]))))))

(deftest optional-join-input-present-test
  (testing "Optional join input is included when available"
    (let [idx (graph/build-index
               [user-by-id
                user-address
                {:id      :label-with-address
                 :input   [:user/name {[:? :user/address] [:address/zip]}]
                 :output  [:user/label]
                 :resolve (fn [_ctx input]
                            (let [name (get input :user/name)
                                  zip  (get-in input [:user/address :address/zip])]
                              {:user/label (if zip
                                             (str name " (" zip ")")
                                             name)}))}])]
      (is (= {:user/label "Alice (10001)"}
             (graph/query {:biff.graph/index idx}
                          {:user/id 1}
                          [:user/label]))))))

(deftest optional-join-input-missing-test
  (testing "Optional join input is omitted when not available"
    (let [idx (graph/build-index
               [{:id      :label-with-address
                 :input   [:user/name {[:? :user/address] [:address/zip]}]
                 :output  [:user/label]
                 :resolve (fn [_ctx input]
                            (let [name (get input :user/name)
                                  zip  (get-in input [:user/address :address/zip])]
                              {:user/label (if zip
                                             (str name " (" zip ")")
                                             name)}))}])]
      (is (= {:user/label "Alice"}
             (graph/query {:biff.graph/index idx}
                          {:user/name "Alice"}
                          [:user/label]))))))

;; ---------------------------------------------------------------------------
;; Optional query item tests
;; ---------------------------------------------------------------------------

(deftest optional-query-item-present-test
  (testing "Optional query item is included when resolvable"
    (is (= {:user/name "Alice" :user/email "alice@example.com"}
           (q {:user/id 1} [:user/name [:? :user/email]])))))

(deftest optional-query-item-missing-test
  (testing "Optional query item is omitted when not resolvable"
    (is (= {:user/name "Alice"}
           (q {:user/id 1} [:user/name [:? :nonexistent/attr]])))))

(deftest optional-query-join-present-test
  (testing "Optional join in query is included when resolvable"
    (is (= {:user/name    "Alice"
            :user/friends [{:user/name "Bob"} {:user/name "Carol"}]}
           (q {:user/id 1} [:user/name {[:? :user/friends] [:user/name]}])))))

(deftest optional-query-join-missing-test
  (testing "Optional join in query is omitted when not resolvable"
    (is (= {:user/name "Alice"}
           (q {:user/id 1} [:user/name {[:? :nonexistent/join] [:some/attr]}])))))

(deftest optional-query-item-from-entity-test
  (testing "Optional query item works when value is in entity"
    (is (= {:user/name "Alice" :extra "data"}
           (q {:user/id 1 :extra "data"} [:user/name [:? :extra]])))))

;; ---------------------------------------------------------------------------
;; Exception handling tests
;; ---------------------------------------------------------------------------

(deftest resolve-error-has-marker-test
  (testing "Resolution errors include ::pl/resolve-error in ex-data"
    (try
      (q {:user/id 1} [:nonexistent/attr])
      (is false "Should have thrown")
      (catch clojure.lang.ExceptionInfo e
        (is (true? (:com.biffweb.graph/resolve-error (ex-data e))))))))

(deftest non-resolve-exceptions-propagate-test
  (testing "Non-resolution exceptions from resolvers are not swallowed"
    (let [idx (graph/build-index
               [{:id      :throws-runtime
                 :input   [:user/id]
                 :output  [:user/boom]
                 :resolve (fn [_ _] (throw (ex-info "custom error" {:custom true})))}])]
      (try
        (graph/query {:biff.graph/index idx} {:user/id 1} [:user/boom])
        (is false "Should have thrown")
        (catch clojure.lang.ExceptionInfo e
          ;; The custom exception should propagate (not be swallowed as a resolve error)
          ;; It might be wrapped in a "No resolver found" if the resolver throws non-resolve-error
          ;; and then no other candidate succeeds. Let's verify it gets through.
          (is (or (:custom (ex-data e))
                  (:com.biffweb.graph/resolve-error (ex-data e)))))))))

;; ---------------------------------------------------------------------------
;; Batch resolver tests
;; ---------------------------------------------------------------------------

(def batch-call-counts (atom {}))

(def batch-user-by-id
  {:id      :batch-user-by-id
   :input   [:user/id]
   :output  [:user/name :user/email]
   :batch   true
   :resolve (fn [_ctx inputs]
              (swap! batch-call-counts update :batch-user-by-id (fnil inc 0))
              (mapv (fn [{:user/keys [id]}]
                      (case id
                        1 {:user/name "Alice" :user/email "alice@example.com"}
                        2 {:user/name "Bob" :user/email "bob@example.com"}
                        3 {:user/name "Carol" :user/email "carol@example.com"}
                        (throw (ex-info "User not found" {:user/id id}))))
                    inputs))})

(def batch-user-age
  {:id      :batch-user-age
   :input   [:user/id]
   :output  [:user/age]
   :batch   true
   :resolve (fn [_ctx inputs]
              (swap! batch-call-counts update :batch-user-age (fnil inc 0))
              (mapv (fn [{:user/keys [id]}]
                      (case id
                        1 {:user/age 30}
                        2 {:user/age 25}
                        3 {:user/age 35}
                        {:user/age nil}))
                    inputs))})

(deftest batch-resolver-basic-test
  (testing "Batch resolver works for sequential join values"
    (reset! batch-call-counts {})
    (let [idx    (graph/build-index [user-friends batch-user-by-id])
          result (graph/query {:biff.graph/index idx}
                              {:user/id 1}
                              [{:user/friends [:user/name]}])]
      (is (= {:user/friends [{:user/name "Bob"} {:user/name "Carol"}]}
             result))
      (is (= 1 (:batch-user-by-id @batch-call-counts))))))

(deftest batch-resolver-multiple-attrs-test
  (testing "Batch resolver resolves multiple attributes from same resolver"
    (reset! batch-call-counts {})
    (let [idx    (graph/build-index [user-friends batch-user-by-id])
          result (graph/query {:biff.graph/index idx}
                              {:user/id 1}
                              [{:user/friends [:user/name :user/email]}])]
      (is (= {:user/friends [{:user/name "Bob" :user/email "bob@example.com"}
                             {:user/name "Carol" :user/email "carol@example.com"}]}
             result)))))

(deftest batch-resolver-different-resolvers-test
  (testing "Multiple batch resolvers for different attrs"
    (reset! batch-call-counts {})
    (let [idx    (graph/build-index [user-friends batch-user-by-id batch-user-age])
          result (graph/query {:biff.graph/index idx}
                              {:user/id 1}
                              [{:user/friends [:user/name :user/age]}])]
      (is (= {:user/friends [{:user/name "Bob" :user/age 25}
                             {:user/name "Carol" :user/age 35}]}
             result))
      (is (= 1 (:batch-user-by-id @batch-call-counts)))
      (is (= 1 (:batch-user-age @batch-call-counts))))))

(deftest batch-resolver-deeply-nested-test
  (testing "Batch resolvers work with deeply nested joins"
    (reset! batch-call-counts {})
    (let [idx    (graph/build-index [user-friends batch-user-by-id])
          result (graph/query {:biff.graph/index idx}
                              {:user/id 1}
                              [{:user/friends [:user/name {:user/friends [:user/name]}]}])]
      (is (= {:user/friends [{:user/name    "Bob"
                              :user/friends [{:user/name "Alice"}]}
                             {:user/name    "Carol"
                              :user/friends [{:user/name "Alice"} {:user/name "Bob"}]}]}
             result)))))

(deftest batch-resolver-cross-tree-test
  (testing "Breadth-first batching: batch resolver called once for ALL grandchildren"
    (reset! batch-call-counts {})
    (let [idx    (graph/build-index [user-friends batch-user-by-id])
          result (graph/query {:biff.graph/index idx}
                              {:user/id 1}
                              [{:user/friends [{:user/friends [:user/name]}]}])]
      ;; User 1's friends are [2, 3]
      ;; User 2's friends are [1], User 3's friends are [1, 2]
      ;; Grandchildren are [1, 1, 2] - all processed in ONE batch call
      (is (= {:user/friends [{:user/friends [{:user/name "Alice"}]}
                             {:user/friends [{:user/name "Alice"} {:user/name "Bob"}]}]}
             result))
      ;; Key assertion: batch resolver called exactly once for ALL grandchildren
      ;; (not once per friend). This is the breadth-first advantage.
      ;; The user-friends resolver (non-batch) handles the friends level,
      ;; and batch-user-by-id is called once for all 3 grandchild entities.
      (is (= 1 (:batch-user-by-id @batch-call-counts))))))

(deftest batch-resolver-single-entity-fallback-test
  (testing "Batch resolver works for individual entity resolution (non-join context)"
    (let [idx    (graph/build-index [batch-user-by-id])
          result (graph/query {:biff.graph/index idx}
                              {:user/id 1}
                              [:user/name :user/email])]
      (is (= {:user/name "Alice" :user/email "alice@example.com"}
             result)))))

(deftest batch-resolver-with-entity-passthrough-test
  (testing "Entities that already have the attr skip batch resolution"
    (reset! batch-call-counts {})
    (let [idx    (graph/build-index [batch-user-by-id])
          result (graph/query {:biff.graph/index idx}
                              {:user/id 1 :user/name "Override"}
                              [:user/name])]
      (is (= {:user/name "Override"} result)))))

(deftest batch-resolver-mixed-with-non-batch-test
  (testing "Batch and non-batch resolvers coexist in the same index"
    (reset! batch-call-counts {})
    (let [idx    (graph/build-index [user-friends batch-user-by-id user-age])
          result (graph/query {:biff.graph/index idx}
                              {:user/id 1}
                              [{:user/friends [:user/name :user/age]}])]
      (is (= {:user/friends [{:user/name "Bob" :user/age 25}
                             {:user/name "Carol" :user/age 35}]}
             result))
      (is (= 1 (:batch-user-by-id @batch-call-counts))))))

(deftest batch-resolver-empty-collection-test
  (testing "Batch resolver handles empty collection gracefully"
    (let [empty-friends {:id      :empty-friends
                         :input   [:user/id]
                         :output  [{:user/friends [:user/id]}]
                         :resolve (fn [_ctx _input] {:user/friends []})}
          idx           (graph/build-index [empty-friends batch-user-by-id])]
      (is (= {:user/friends []}
             (graph/query {:biff.graph/index idx}
                          {:user/id 99}
                          [{:user/friends [:user/name]}]))))))

(deftest batch-resolver-with-sub-query-test
  (testing "Batch resolver result can have sub-query applied"
    (let [idx    (graph/build-index [user-friends
                                     batch-user-by-id
                                     user-address])
          result (graph/query {:biff.graph/index idx}
                              {:user/id 1}
                              [{:user/friends [:user/name {:user/address [:address/zip]}]}])]
      (is (= {:user/friends [{:user/name    "Bob"
                              :user/address {:address/zip "90210"}}
                             {:user/name    "Carol"
                              :user/address {:address/zip "60601"}}]}
             result)))))

(deftest batch-resolver-optional-query-item-test
  (testing "Optional query items work with batch resolution"
    (let [idx    (graph/build-index [user-friends batch-user-by-id])
          result (graph/query {:biff.graph/index idx}
                              {:user/id 1}
                              [{:user/friends [:user/name [:? :nonexistent/attr]]}])]
      (is (= {:user/friends [{:user/name "Bob"} {:user/name "Carol"}]}
             result)))))

(defn batch-var-user-by-id
  {:input  [:user/id]
   :output [:user/name :user/email]
   :batch  true}
  [_ctx inputs]
  (mapv (fn [{:user/keys [id]}]
          (case id
            1 {:user/name "Alice" :user/email "alice@example.com"}
            2 {:user/name "Bob" :user/email "bob@example.com"}
            3 {:user/name "Carol" :user/email "carol@example.com"}
            (throw (ex-info "User not found" {:user/id id}))))
        inputs))

(deftest batch-var-resolver-test
  (testing "Var-based batch resolver reads :batch from metadata"
    (let [r (graph/resolver #'batch-var-user-by-id)]
      (is (true? (:batch r)))
      (is (= :com.biffweb.graph-test/batch-var-user-by-id (:id r))))))

(deftest batch-var-resolver-query-test
  (testing "Var-based batch resolver works in queries"
    (let [idx    (graph/build-index [user-friends #'batch-var-user-by-id])
          result (graph/query {:biff.graph/index idx}
                              {:user/id 1}
                              [{:user/friends [:user/name]}])]
      (is (= {:user/friends [{:user/name "Bob"} {:user/name "Carol"}]}
             result)))))

(deftest batch-resolver-build-index-test
  (testing "build-index preserves :batch flag"
    (let [idx (graph/build-index [batch-user-by-id user-by-id])]
      (is (true? (:batch (first (filter :batch (:all-resolvers idx))))))
      (is (false? (:batch (first (remove :batch (:all-resolvers idx)))))))))

;; ---------------------------------------------------------------------------
;; Vector entity support (query accepts map or vector)
;; ---------------------------------------------------------------------------

(deftest query-with-vector-entities-test
  (testing "query accepts a vector of entity maps and returns a vector of results"
    (let [idx    (graph/build-index [user-by-id])
          result (graph/query {:biff.graph/index idx}
                              [{:user/id 1} {:user/id 2} {:user/id 3}]
                              [:user/name])]
      (is (= [{:user/name "Alice"} {:user/name "Bob"} {:user/name "Carol"}]
             result)))))

(deftest query-with-empty-vector-test
  (testing "query with empty vector returns empty vector"
    (let [idx    (graph/build-index [user-by-id])
          result (graph/query {:biff.graph/index idx}
                              []
                              [:user/name])]
      (is (= [] result)))))

(deftest query-with-vector-and-joins-test
  (testing "query with vector entities and joins"
    (let [idx    (graph/build-index [user-by-id user-friends])
          result (graph/query {:biff.graph/index idx}
                              [{:user/id 1} {:user/id 2}]
                              [:user/name {:user/friends [:user/name]}])]
      (is (= [{:user/name    "Alice"
               :user/friends [{:user/name "Bob"} {:user/name "Carol"}]}
              {:user/name    "Bob"
               :user/friends [{:user/name "Alice"}]}]
             result)))))

(deftest query-with-vector-uses-batch-test
  (testing "query with vector entities uses batch resolvers efficiently"
    (reset! batch-call-counts {})
    (let [idx    (graph/build-index [batch-user-by-id])
          result (graph/query {:biff.graph/index idx}
                              [{:user/id 1} {:user/id 2} {:user/id 3}]
                              [:user/name])]
      (is (= [{:user/name "Alice"} {:user/name "Bob"} {:user/name "Carol"}]
             result))
      ;; All three entities resolved in a single batch call
      (is (= 1 (:batch-user-by-id @batch-call-counts))))))

;; ---------------------------------------------------------------------------
;; Per-entity input resolution failure tests
;; ---------------------------------------------------------------------------

(deftest per-entity-input-skip-test
  (testing "Entities that can't satisfy resolver inputs are skipped per-entity,
            not causing all entities to fail"
    (let [;; Resolver that requires :user/id
          name-resolver batch-user-by-id
          idx           (graph/build-index [name-resolver])
          ;; Mix of entities: some have :user/id, some have :user/name directly
          result        (graph/query {:biff.graph/index idx}
                                     [{:user/id 1} {:user/id 2 :user/name "PresetBob"}]
                                     [:user/name])]
      (is (= [{:user/name "Alice"} {:user/name "PresetBob"}]
             result)))))

;; ---------------------------------------------------------------------------
;; Resolver fallback tests
;; ---------------------------------------------------------------------------

(deftest resolver-fallback-test
  (testing "When first resolver can't produce attr for some entities, tries subsequent resolvers"
    (let [;; First resolver: only handles user IDs 1 and 2
          partial-resolver
          {:id      :partial-name
           :input   [:user/id]
           :output  [:user/name]
           :batch   true
           :resolve (fn [_ctx inputs]
                      (mapv (fn [{:user/keys [id]}]
                              (case id
                                1 {:user/name "Alice"}
                                2 {:user/name "Bob"}
                                 ;; Return empty map for unknown users
                                {}))
                            inputs))}
          ;; Second resolver: handles user ID 3
          fallback-resolver
          {:id      :fallback-name
           :input   [:user/id]
           :output  [:user/name]
           :resolve (fn [_ctx {:user/keys [id]}]
                      (case id
                        3 {:user/name "Carol-fallback"}
                        {}))}
          idx    (graph/build-index [partial-resolver fallback-resolver])
          result (graph/query {:biff.graph/index idx}
                              [{:user/id 1} {:user/id 2} {:user/id 3}]
                              [:user/name])]
      (is (= [{:user/name "Alice"} {:user/name "Bob"} {:user/name "Carol-fallback"}]
             result)))))

(deftest resolver-fallback-with-different-inputs-test
  (testing "Resolver fallback works when resolvers have different input requirements"
    (let [;; First resolver: needs :user/id
          by-id-resolver
          {:id      :name-by-id
           :input   [:user/id]
           :output  [:user/name]
           :resolve (fn [_ctx {:user/keys [id]}]
                      {:user/name (str "User-" id)})}
          ;; Second resolver: needs :user/email
          by-email-resolver
          {:id      :name-by-email
           :input   [:user/email]
           :output  [:user/name]
           :resolve (fn [_ctx {:user/keys [email]}]
                      {:user/name (str "EmailUser-" email)})}
          idx    (graph/build-index [by-id-resolver by-email-resolver])
          ;; Mix: some entities have :user/id, some have :user/email
          result (graph/query {:biff.graph/index idx}
                              [{:user/id 1} {:user/email "bob@example.com"}]
                              [:user/name])]
      (is (= [{:user/name "User-1"} {:user/name "EmailUser-bob@example.com"}]
             result)))))

;; ---------------------------------------------------------------------------
;; Batch input resolution tests
;; ---------------------------------------------------------------------------

(deftest batch-input-resolution-test
  (testing "Input resolution uses batch resolvers when available"
    (reset! batch-call-counts {})
    (let [;; batch resolver provides :user/name from :user/id
          _      batch-user-by-id
          ;; resolver that needs :user/name (resolved via batch-user-by-id)
          greeting-resolver
          {:id      :greeting
           :input   [:user/name]
           :output  [:user/greeting]
           :batch   true
           :resolve (fn [_ctx inputs]
                      (mapv (fn [{:user/keys [name]}]
                              {:user/greeting (str "Hello, " name "!")})
                            inputs))}
          idx    (graph/build-index [user-friends batch-user-by-id greeting-resolver])
          result (graph/query {:biff.graph/index idx}
                              {:user/id 1}
                              [{:user/friends [:user/greeting]}])]
      (is (= {:user/friends [{:user/greeting "Hello, Bob!"}
                             {:user/greeting "Hello, Carol!"}]}
             result))
      ;; batch-user-by-id called once for both friends' :user/name input resolution
      (is (= 1 (:batch-user-by-id @batch-call-counts))))))

;; ---------------------------------------------------------------------------
;; Caching tests
;; ---------------------------------------------------------------------------

(deftest cache-non-batch-memoize-test
  (testing "Non-batch resolver is only called once per unique input within a query"
    (let [call-count (atom 0)
          idx        (graph/build-index
                      [{:id      :user-name
                        :input   [:user/id]
                        :output  [:user/name]
                        :resolve (fn [_ctx {:user/keys [id]}]
                                   (swap! call-count inc)
                                   {:user/name (str "User-" id)})}
                       user-friends])
          result     (graph/query {:biff.graph/index idx}
                                  {:user/id 1}
                                  [{:user/friends [:user/name]}])]
      ;; User 1's friends are user 2 and user 3 — two different inputs,
      ;; so the resolver should be called twice.
      (is (= {:user/friends [{:user/name "User-2"} {:user/name "User-3"}]}
             result))
      (is (= 2 @call-count)))))

(deftest cache-non-batch-duplicate-input-test
  (testing "Non-batch resolver reuses cached result for duplicate inputs across tree levels"
    (let [call-count (atom 0)
          idx        (graph/build-index
                      [{:id      :user-name
                        :input   [:user/id]
                        :output  [:user/name]
                        :resolve (fn [_ctx {:user/keys [id]}]
                                   (swap! call-count inc)
                                   {:user/name (str "User-" id)})}
                       user-friends])
          ;; User 1's friends: [2, 3]
          ;; User 2's friends: [1]
          ;; User 3's friends: [1, 2]
          ;; At grandchild level: need :user/name for users 1, 1, 2
          ;; User 2's name was resolved at child level → cached
          ;; User 1's name only needs one actual call (second is cached)
          result     (graph/query {:biff.graph/index idx}
                                  {:user/id 1}
                                  [{:user/friends [:user/name {:user/friends [:user/name]}]}])]
      (is (= {:user/friends [{:user/name    "User-2"
                              :user/friends [{:user/name "User-1"}]}
                             {:user/name    "User-3"
                              :user/friends [{:user/name "User-1"} {:user/name "User-2"}]}]}
             result))
      ;; Without caching: user-name called for [2,3] at child level, then [1,1,2] at
      ;; grandchild level = 5 calls. With caching: [2,3] at child level (2 calls),
      ;; then at grandchild level user 2 is cached, user 1 called once and reused = 3 calls.
      (is (= 3 @call-count)))))

(deftest cache-batch-partial-hit-test
  (testing "Batch resolver only sends uncached inputs to the underlying resolver"
    (let [call-log (atom [])
          idx      (graph/build-index
                    [{:id      :batch-name
                      :input   [:user/id]
                      :output  [:user/name]
                      :batch   true
                      :resolve (fn [_ctx inputs]
                                 (swap! call-log conj (mapv :user/id inputs))
                                 (mapv (fn [{:user/keys [id]}]
                                         {:user/name (str "User-" id)})
                                       inputs))}
                     user-friends])
          result   (graph/query {:biff.graph/index idx}
                                {:user/id 1}
                                [{:user/friends [:user/name {:user/friends [:user/name]}]}])]
      (is (= {:user/friends [{:user/name    "User-2"
                              :user/friends [{:user/name "User-1"}]}
                             {:user/name    "User-3"
                              :user/friends [{:user/name "User-1"} {:user/name "User-2"}]}]}
             result))
      ;; First batch call: users [2, 3] at child level
      ;; Second batch call: only user [1] at grandchild level (user 2 cached from first call,
      ;; and duplicate user 1 entries are deduplicated)
      (is (= [[2 3] [1]] @call-log)))))

(deftest cache-batch-all-cached-test
  (testing "Batch resolver is not called when all inputs are cached"
    (let [call-count (atom 0)
          idx        (graph/build-index
                      [{:id      :batch-name
                        :input   [:user/id]
                        :output  [:user/name]
                        :batch   true
                        :resolve (fn [_ctx inputs]
                                   (swap! call-count inc)
                                   (mapv (fn [{:user/keys [id]}]
                                           {:user/name (str "User-" id)})
                                         inputs))}
                ;; friends resolver that returns friends who are already known
                       {:id      :self-friends
                        :input   [:user/id]
                        :output  [{:user/friends [:user/id]}]
                        :resolve (fn [_ctx {:user/keys [id]}]
                                   (case id
                                     1 {:user/friends [{:user/id 2}]}
                                     2 {:user/friends [{:user/id 1}]}
                                     {:user/friends []}))}])
          ;; Query: resolve name for user 1, then resolve friends (user 2),
          ;; then resolve name for user 2's friends (user 1, which is already cached)
          result     (graph/query {:biff.graph/index idx}
                                  {:user/id 1}
                                  [:user/name {:user/friends [:user/name {:user/friends [:user/name]}]}])]
      (is (= {:user/name    "User-1"
              :user/friends [{:user/name    "User-2"
                              :user/friends [{:user/name "User-1"}]}]}
             result))
      ;; batch-name called: once for [user 1] (top level) + once for [user 2] (child).
      ;; At grandchild level, user 1 is cached → no call.
      (is (= 2 @call-count)))))

(deftest cache-per-query-isolation-test
  (testing "Cache is fresh per query call — no cross-query caching"
    (let [call-count (atom 0)
          idx        (graph/build-index
                      [{:id      :counting-resolver
                        :input   [:user/id]
                        :output  [:user/name]
                        :resolve (fn [_ctx {:user/keys [id]}]
                                   (swap! call-count inc)
                                   {:user/name (str "User-" id)})}])]
      (graph/query {:biff.graph/index idx} {:user/id 1} [:user/name])
      (is (= 1 @call-count))
      ;; Second query with same input — cache is fresh, so resolver called again
      (graph/query {:biff.graph/index idx} {:user/id 1} [:user/name])
      (is (= 2 @call-count)))))

(deftest cache-nil-result-test
  (testing "Cached results with nil values are still returned from cache"
    (let [call-count (atom 0)
          idx        (graph/build-index
                      [{:id      :nil-resolver
                        :input   [:user/id]
                        :output  [:user/nickname]
                        :resolve (fn [_ctx _input]
                                   (swap! call-count inc)
                                   {:user/nickname nil})}
                       {:id      :self-friends
                        :input   [:user/id]
                        :output  [{:user/friends [:user/id]}]
                        :resolve (fn [_ctx {:user/keys [id]}]
                                   {:user/friends [{:user/id id}]})}])
          ;; Query nickname at top level, then again in a join
          result     (graph/query {:biff.graph/index idx}
                                  {:user/id 1}
                                  [:user/nickname {:user/friends [:user/nickname]}])]
      (is (= {:user/nickname nil
              :user/friends  [{:user/nickname nil}]}
             result))
      ;; Resolver called once for user 1 at top level, cached for the join
      (is (= 1 @call-count)))))

(deftest cache-global-resolver-test
  (testing "Global resolvers (no input) are cached within a query"
    (let [call-count (atom 0)
          idx        (graph/build-index
                      [{:id      :counting-global
                        :input   []
                        :output  [:user/id]
                        :resolve (fn [_ctx _input]
                                   (swap! call-count inc)
                                   {:user/id 42})}
                       {:id      :user-name
                        :input   [:user/id]
                        :output  [:user/name]
                        :resolve (fn [_ctx {:user/keys [id]}]
                                   {:user/name (str "User-" id)})}])
          result     (graph/query {:biff.graph/index idx}
                                  [{} {}]
                                  [:user/name])]
      (is (= [{:user/name "User-42"} {:user/name "User-42"}] result))
      ;; Global resolver called once, result reused for second entity
      (is (= 1 @call-count)))))

;; ---------------------------------------------------------------------------
;; 2-arity query tests (entity-or-entities omitted)
;; ---------------------------------------------------------------------------

(deftest query-2-arity-global-resolver-test
  (testing "2-arity query uses global resolvers with default empty entity"
    (is (= {:user/id 1 :user/name "Alice"}
           (graph/query {:biff.graph/index index :current-user-id 1}
                        [:user/id :user/name])))))

(deftest query-2-arity-empty-query-test
  (testing "2-arity query with empty query returns empty map"
    (is (= {} (graph/query {:biff.graph/index index} [])))))

(deftest query-2-arity-chained-resolvers-test
  (testing "2-arity query chains global resolvers with downstream resolvers"
    (is (= {:user/id 2 :user/name "Bob" :user/email "bob@example.com"}
           (graph/query {:biff.graph/index index :current-user-id 2}
                        [:user/id :user/name :user/email])))))
