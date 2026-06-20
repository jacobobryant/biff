(ns com.biffweb.graph.errors-test
  (:require [clojure.repl :as repl]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [com.biffweb.graph :as graph]
            [com.biffweb.graph.impl :as impl]))

(defn- class-name [x]
  (symbol (.getName ^Class x)))

(defn- error-message [t]
  (cond-> (ex-message t)
    (instance? clojure.lang.Compiler$CompilerException t)
    (str/replace #"\(NO_SOURCE_FILE:\d+:\d+\)" "(NO_SOURCE_FILE)")))

(defn- error-data [t]
  (if (instance? clojure.lang.Compiler$CompilerException t)
    (select-keys (ex-data t) [:clojure.error/phase
                              :clojure.error/source])
    (ex-data t)))

(defn- cause-summary [t]
  (loop [causes []
         t      t]
    (if t
      (recur (conj causes {:class   (class-name (class t))
                           :message (error-message t)
                           :data    (error-data t)})
             (ex-cause t))
      causes)))

(defn- stack-summary [t]
  (->> (.getStackTrace ^Throwable t)
       (keep (fn [^StackTraceElement frame]
               (let [f (repl/demunge (.getClassName frame))]
                 (when (and (str/starts-with? f "com.biffweb.graph")
                            (not (str/starts-with? f "com.biffweb.graph.errors-test"))
                            (not (str/starts-with? f "com.biffweb.graph.tasks"))
                            (not (str/includes? f "--")))
                   {:fn   (symbol f)
                    :file (.getFileName frame)}))))
       distinct
       (take 8)
       vec))

(defn error-summary [f]
  (try
    (f)
    ::no-error
    (catch Throwable t
      {:class   (class-name (class t))
       :message (error-message t)
       :data    (error-data t)
       :causes  (cause-summary t)
       :stack   (stack-summary t)})))

(defn resolver [opts]
  (graph/resolver
   (merge {:id         ::resolver
           :input      []
           :output     []
           :resolve-fn (fn [_ctx _input] {})}
         opts)))

(deftest query->ast-validates-query-test
  (is (= (error-summary #(graph/query->ast [:*]))
         {:class   'java.lang.AssertionError
          :message "`:biff.graph/query [:*]` is invalid: [[\"should not be :*\" \"invalid type\" \"unknown error\" \"invalid type\"]]"
          :data    nil
          :causes  [{:class   'java.lang.AssertionError
                     :message "`:biff.graph/query [:*]` is invalid: [[\"should not be :*\" \"invalid type\" \"unknown error\" \"invalid type\"]]"
                     :data    nil}]
          :stack   [{:fn   'com.biffweb.graph.impl/query->ast
                     :file "impl.clj"}
                    {:fn   'com.biffweb.graph/query->ast
                     :file "graph.clj"}]})))

(deftest resolver-validates-normalized-resolver-test
  (is (= (error-summary #(resolver {:id nil}))
         {:class   'java.lang.AssertionError
          :message "`:biff.graph/id nil` is invalid: should be a qualified keyword"
          :data    nil
          :causes  [{:class   'java.lang.AssertionError
                     :message "`:biff.graph/id nil` is invalid: should be a qualified keyword"
                     :data    nil}]
          :stack   [{:fn   'com.biffweb.graph.impl/resolver
                     :file "impl.clj"}
                    {:fn   'com.biffweb.graph/resolver
                     :file "graph.clj"}]})))

(deftest defresolver-validates-normalized-resolver-test
  (is (= (error-summary
          #(eval '(com.biffweb.graph.impl/defresolver invalid-defresolver
                    {:batch :not-boolean}
                    [_ctx _input]
                    {})))
         {:class   'clojure.lang.Compiler$CompilerException
          :message "Syntax error macroexpanding at (NO_SOURCE_FILE)."
          :data    {:clojure.error/phase  :execution
                    :clojure.error/source "NO_SOURCE_FILE"}
          :causes  [{:class   'clojure.lang.Compiler$CompilerException
                     :message "Syntax error macroexpanding at (NO_SOURCE_FILE)."
                     :data    {:clojure.error/phase  :execution
                               :clojure.error/source "NO_SOURCE_FILE"}}
                    {:class   'java.lang.AssertionError
                     :message "`:biff.graph/batch :not-boolean` is invalid: should be a boolean"
                     :data    nil}]
          :stack   []})))

(deftest select-output-rejects-scalar-output-for-join-test
  (let [env (graph/new-env [(resolver {:output     [{:x [:y]}]
                                       :resolve-fn (fn [_ctx _input]
                                                     {:x 1})})])]
    (is (= (error-summary #(graph/query env [{:x [:y]}]))
           {:class   'java.lang.AssertionError
            :message "Assert failed: :x was declared as a join but value is a scalar\n(join-value? value)"
            :data    nil
            :causes  [{:class   'java.lang.AssertionError
                       :message "Assert failed: :x was declared as a join but value is a scalar\n(join-value? value)"
                       :data    nil}]
            :stack   [{:fn   'com.biffweb.graph.impl/select-output-value
                       :file "impl.clj"}
                      {:fn   'com.biffweb.graph.impl/select-output
                       :file "impl.clj"}
                      {:fn   'com.biffweb.graph.impl/resolve-attr
                       :file "impl.clj"}
                      {:fn   'com.biffweb.graph.impl/resolve-entities
                       :file "impl.clj"}
                      {:fn   'com.biffweb.graph.impl/query
                       :file "impl.clj"}
                      {:fn   'com.biffweb.graph/query
                       :file "graph.clj"}]}))))

(deftest select-output-rejects-join-output-for-scalar-test
  (let [env (graph/new-env [(resolver {:output     [:x]
                                       :resolve-fn (fn [_ctx _input]
                                                     {:x {:y 1}})})])]
    (is (= (error-summary #(graph/query env [:x]))
           {:class   'java.lang.AssertionError
            :message "Assert failed: :x was declared as a scalar but value is a map\n(scalar-value? value)"
            :data    nil
            :causes  [{:class   'java.lang.AssertionError
                       :message "Assert failed: :x was declared as a scalar but value is a map\n(scalar-value? value)"
                       :data    nil}]
            :stack   [{:fn   'com.biffweb.graph.impl/select-output-value
                       :file "impl.clj"}
                      {:fn   'com.biffweb.graph.impl/select-output
                       :file "impl.clj"}
                      {:fn   'com.biffweb.graph.impl/resolve-attr
                       :file "impl.clj"}
                      {:fn   'com.biffweb.graph.impl/resolve-entities
                       :file "impl.clj"}
                      {:fn   'com.biffweb.graph.impl/query
                       :file "impl.clj"}
                      {:fn   'com.biffweb.graph/query
                       :file "graph.clj"}]}))))

(deftest wrap-validate-validates-resolver-output-test
  (let [env (graph/new-env [(resolver {:output     [:biff.graph/id]
                                       :resolve-fn (fn [_ctx _input]
                                                     {:biff.graph/id "not-a-keyword"})})])]
    (is (= (error-summary #(graph/query env [:biff.graph/id]))
           {:class   'java.lang.AssertionError
            :message "`:biff.graph/id \"not-a-keyword\"` is invalid: should be a qualified keyword"
            :data    nil
            :causes  [{:class   'java.lang.AssertionError
                       :message "`:biff.graph/id \"not-a-keyword\"` is invalid: should be a qualified keyword"
                       :data    nil}]
            :stack   [{:fn   'com.biffweb.graph.impl/resolve-attr
                       :file "impl.clj"}
                      {:fn   'com.biffweb.graph.impl/resolve-entities
                       :file "impl.clj"}
                      {:fn   'com.biffweb.graph.impl/query
                       :file "impl.clj"}
                      {:fn   'com.biffweb.graph/query
                       :file "graph.clj"}]}))))

(deftest new-env-rejects-conflicting-attribute-shapes-test
  (is (= (error-summary
          #(graph/new-env [(resolver {:id     ::scalar-x
                                      :output [:x]})
                           (resolver {:id     ::join-x
                                      :output [{:x [:y]}]})]))
         {:class   'java.lang.AssertionError
          :message "Assert failed: :x has conflicting shapes: {:kind :scalar}, {:kind :join}\n(= shape (get attr->shape attr))"
          :data    nil
          :causes  [{:class   'java.lang.AssertionError
                     :message "Assert failed: :x has conflicting shapes: {:kind :scalar}, {:kind :join}\n(= shape (get attr->shape attr))"
                     :data    nil}]
          :stack   [{:fn   'com.biffweb.graph.impl/validate-query
                     :file "impl.clj"}
                    {:fn   'com.biffweb.graph.impl/new-env
                     :file "impl.clj"}
                    {:fn   'com.biffweb.graph/new-env
                     :file "graph.clj"}]})))

(deftest new-env-validates-resolver-maps-test
  (is (= (error-summary
          #(graph/new-env [{:biff.graph/id         ::bad
                            :biff.graph/resolve-fn (fn [_ctx] {})}]))
         {:class   'java.lang.AssertionError
          :message "Missing required key: :biff.graph/output-ast"
          :data    nil
          :causes  [{:class   'java.lang.AssertionError
                     :message "Missing required key: :biff.graph/output-ast"
                     :data    nil}]
          :stack   [{:fn   'com.biffweb.graph.impl/new-env
                     :file "impl.clj"}
                    {:fn   'com.biffweb.graph/new-env
                     :file "graph.clj"}]})))

(deftest resolve-attr-requires-vector-entities-test
  (is (= (error-summary #(impl/resolve-attr {:biff.graph/attr->resolvers {}}
                                            {}
                                            :x
                                            #{}))
         {:class   'java.lang.AssertionError
          :message "Assert failed: (vector? entities)"
          :data    nil
          :causes  [{:class   'java.lang.AssertionError
                     :message "Assert failed: (vector? entities)"
                     :data    nil}]
          :stack   [{:fn   'com.biffweb.graph.impl/resolve-attr
                     :file "impl.clj"}]})))

(deftest partition-by-sizes-requires-vector-input-test
  (is (= (error-summary #(impl/partition-by-sizes '(:a :b) [1 1]))
         {:class   'java.lang.AssertionError
          :message "Assert failed: (vector? v)"
          :data    nil
          :causes  [{:class   'java.lang.AssertionError
                     :message "Assert failed: (vector? v)"
                     :data    nil}]
          :stack   [{:fn   'com.biffweb.graph.impl/partition-by-sizes
                     :file "impl.clj"}]})))

(deftest partition-by-sizes-requires-vector-sizes-test
  (is (= (error-summary #(impl/partition-by-sizes [:a :b] '(1 1)))
         {:class   'java.lang.AssertionError
          :message "Assert failed: (vector? sizes)"
          :data    nil
          :causes  [{:class   'java.lang.AssertionError
                     :message "Assert failed: (vector? sizes)"
                     :data    nil}]
          :stack   [{:fn   'com.biffweb.graph.impl/partition-by-sizes
                     :file "impl.clj"}]})))

(deftest resolve-joins-rejects-conflicting-cardinalities-test
  (let [env (graph/new-env [(resolver {:input      [:id]
                                       :output     [{:x [:y]}]
                                       :resolve-fn (fn [_ctx {:keys [id]}]
                                                     {:x (if (= id 1)
                                                           {:y 1}
                                                           [{:y 2}])})})])]
    (is (= (error-summary #(graph/query env
                                        [{:id 1} {:id 2}]
                                        [{:x [:y]}]))
           {:class   'clojure.lang.ExceptionInfo
            :message "Got conflicting cardinalities"
            :data    {}
            :causes  [{:class   'clojure.lang.ExceptionInfo
                       :message "Got conflicting cardinalities"
                       :data    {}}]
            :stack   [{:fn   'com.biffweb.graph.impl/resolve-joins
                       :file "impl.clj"}
                      {:fn   'com.biffweb.graph.impl/resolve-entities
                       :file "impl.clj"}
                      {:fn   'com.biffweb.graph.impl/query
                       :file "impl.clj"}
                      {:fn   'com.biffweb.graph/query
                       :file "graph.clj"}]}))))

(deftest query-validates-query-test
  (is (= (error-summary #(graph/query {} [:*]))
         {:class   'java.lang.AssertionError
          :message "`:biff.graph/query [:*]` is invalid: [[\"should not be :*\" \"invalid type\" \"unknown error\" \"invalid type\"]]"
          :data    nil
          :causes  [{:class   'java.lang.AssertionError
                     :message "`:biff.graph/query [:*]` is invalid: [[\"should not be :*\" \"invalid type\" \"unknown error\" \"invalid type\"]]"
                     :data    nil}]
          :stack   [{:fn   'com.biffweb.graph.impl/query
                     :file "impl.clj"}
                    {:fn   'com.biffweb.graph/query
                     :file "graph.clj"}]})))

(deftest query-validates-input-test
  (is (= (error-summary #(graph/query {} :invalid-input [:x]))
         {:class   'java.lang.AssertionError
          :message "`:biff.graph/input :invalid-input` is invalid: [\"invalid type\" \"should be a map\"]"
          :data    nil
          :causes  [{:class   'java.lang.AssertionError
                     :message "`:biff.graph/input :invalid-input` is invalid: [\"invalid type\" \"should be a map\"]"
                     :data    nil}]
          :stack   [{:fn   'com.biffweb.graph.impl/query
                     :file "impl.clj"}
                    {:fn   'com.biffweb.graph/query
                     :file "graph.clj"}]})))

(deftest query-validates-env-test
  (is (= (error-summary #(graph/query {} [:x]))
         {:class   'java.lang.AssertionError
          :message "Missing required keys: :biff.graph/attr->resolvers, :biff.graph/attr->shape"
          :data    nil
          :causes  [{:class   'java.lang.AssertionError
                     :message "Missing required keys: :biff.graph/attr->resolvers, :biff.graph/attr->shape"
                     :data    nil}]
          :stack   [{:fn   'com.biffweb.graph.impl/query
                     :file "impl.clj"}
                    {:fn   'com.biffweb.graph/query
                     :file "graph.clj"}]})))

(deftest query-rejects-conflicting-query-shapes-test
  (is (= (error-summary
          #(graph/query {:biff.graph/attr->resolvers {}
                         :biff.graph/attr->shape     {:x {:kind :scalar}}}
                        [{:x [:y]}]))
         {:class   'java.lang.AssertionError
          :message "Assert failed: :x has conflicting shapes: {:kind :join}, {:kind :scalar}\n(= shape (get attr->shape attr))"
          :data    nil
          :causes  [{:class   'java.lang.AssertionError
                     :message "Assert failed: :x has conflicting shapes: {:kind :join}, {:kind :scalar}\n(= shape (get attr->shape attr))"
                     :data    nil}]
          :stack   [{:fn   'com.biffweb.graph.impl/validate-query
                     :file "impl.clj"}
                    {:fn   'com.biffweb.graph.impl/query
                     :file "impl.clj"}
                    {:fn   'com.biffweb.graph/query
                     :file "graph.clj"}]})))

(deftest query-throws-for-unresolved-required-attribute-test
  (is (= (error-summary
          #(graph/query {:biff.graph/attr->resolvers {}
                         :biff.graph/attr->shape     {:x {:kind :scalar}}}
                        [:x]))
         {:class   'clojure.lang.ExceptionInfo
          :message "TODO"
          :data    {:com.biffweb.graph.impl/unresolved true}
          :causes  [{:class   'clojure.lang.ExceptionInfo
                     :message "TODO"
                     :data    {:com.biffweb.graph.impl/unresolved true}}]
          :stack   [{:fn   'com.biffweb.graph.impl/query
                     :file "impl.clj"}
                    {:fn   'com.biffweb.graph/query
                     :file "graph.clj"}]})))
