(ns com.biffweb.graph.tasks
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [com.biffweb.graph :as graph]
            [com.biffweb.graph.impl :as impl]
            [com.biffweb.run :as biff.run]
            [com.biffweb.tasks.lib :as lib]
            [taoensso.telemere :as tel]))

(defn- resolver [opts]
  (graph/resolver
   (merge {:id         ::resolver
           :input      []
           :output     []
           :resolve-fn (fn [_ctx _input] {})}
          opts)))

(defn- code [& lines]
  (str/join "\n" lines))

(def examples
  [{:title "Invalid Query"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/query->ast [:*])")
    :run   #(graph/query->ast [:*])}

   {:title "Invalid Resolver"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/resolver"
                 " {:id nil"
                 "  :input []"
                 "  :output []"
                 "  :resolve-fn (fn [_ctx _input] {})})")
    :run   #(resolver {:id nil})}

   {:title "Invalid defresolver"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/defresolver invalid-defresolver"
                 "  {:batch :not-boolean}"
                 "  [_ctx _input]"
                 "  {})")
    :run   #(eval '(com.biffweb.graph/defresolver invalid-defresolver
                     {:batch :not-boolean}
                     [_ctx _input]
                     {}))}

   {:title "Resolver Returns Scalar For Join"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(def env"
                 "  (graph/new-env"
                 "   [(graph/resolver"
                 "     {:id :example/x"
                 "      :output [{:x [:y]}]"
                 "      :resolve-fn (fn [_ctx _input] {:x 1})})]))"
                 ""
                 "(graph/query env [{:x [:y]}])")
    :run   #(let [env (graph/new-env [(resolver {:output     [{:x [:y]}]
                                                 :resolve-fn (fn [_ctx _input]
                                                               {:x 1})})])]
              (graph/query env [{:x [:y]}]))}

   {:title "Resolver Returns Join For Scalar"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(def env"
                 "  (graph/new-env"
                 "   [(graph/resolver"
                 "     {:id :example/x"
                 "      :output [:x]"
                 "      :resolve-fn (fn [_ctx _input] {:x {:y 1}})})]))"
                 ""
                 "(graph/query env [:x])")
    :run   #(let [env (graph/new-env [(resolver {:output     [:x]
                                                 :resolve-fn (fn [_ctx _input]
                                                               {:x {:y 1}})})])]
              (graph/query env [:x]))}

   {:title "Resolver Returns Invalid Typed Data"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(def env"
                 "  (graph/new-env"
                 "   [(graph/resolver"
                 "     {:id :example/id"
                 "      :output [:biff.graph/id]"
                 "      :resolve-fn (fn [_ctx _input]"
                 "                    {:biff.graph/id \"not-a-keyword\"})})]))"
                 ""
                 "(graph/query env [:biff.graph/id])")
    :run   #(let [env (graph/new-env [(resolver {:output     [:biff.graph/id]
                                                 :resolve-fn (fn [_ctx _input]
                                                               {:biff.graph/id "not-a-keyword"})})])]
              (graph/query env [:biff.graph/id]))}

   {:title "Conflicting Attribute Shapes In Env"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/new-env"
                 " [(graph/resolver"
                 "   {:id :example/scalar-x"
                 "    :output [:x]"
                 "    :resolve-fn (fn [_ctx _input] {})})"
                 "  (graph/resolver"
                 "   {:id :example/join-x"
                 "    :output [{:x [:y]}]"
                 "    :resolve-fn (fn [_ctx _input] {})})])")
    :run   #(graph/new-env [(resolver {:id     ::scalar-x
                                       :output [:x]})
                            (resolver {:id     ::join-x
                                       :output [{:x [:y]}]})])}

   {:title "Missing Resolver Keys"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/new-env"
                 " [{:biff.graph/id :example/bad"
                 "   :biff.graph/resolve-fn (fn [_ctx] {})}])")
    :run   #(graph/new-env [{:biff.graph/id         ::bad
                             :biff.graph/resolve-fn (fn [_ctx] {})}])}

   {:title "Invalid Internal Entity Input"
    :code  (code "(require '[com.biffweb.graph.impl :as impl])"
                 ""
                 "(impl/resolve-attr {:biff.graph/attr->resolvers {}}"
                 "                   {}"
                 "                   :x"
                 "                   #{})")
    :run   #(impl/resolve-attr {:biff.graph/attr->resolvers {}}
                               {}
                               :x
                               #{})}

   {:title "Invalid Partition Input"
    :code  (code "(require '[com.biffweb.graph.impl :as impl])"
                 ""
                 "(impl/partition-by-sizes '(:a :b) [1 1])")
    :run   #(impl/partition-by-sizes '(:a :b) [1 1])}

   {:title "Invalid Partition Sizes"
    :code  (code "(require '[com.biffweb.graph.impl :as impl])"
                 ""
                 "(impl/partition-by-sizes [:a :b] '(1 1))")
    :run   #(impl/partition-by-sizes [:a :b] '(1 1))}

   {:title "Conflicting Join Cardinalities"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(def env"
                 "  (graph/new-env"
                 "   [(graph/resolver"
                 "     {:id :example/x"
                 "      :input [:id]"
                 "      :output [{:x [:y]}]"
                 "      :resolve-fn (fn [_ctx {:keys [id]}]"
                 "                    {:x (if (= id 1)"
                 "                          {:y 1}"
                 "                          [{:y 2}])})})]))"
                 ""
                 "(graph/query env [{:id 1} {:id 2}] [{:x [:y]}])")
    :run   #(let [env (graph/new-env [(resolver {:input      [:id]
                                                 :output     [{:x [:y]}]
                                                 :resolve-fn (fn [_ctx {:keys [id]}]
                                                               {:x (if (= id 1)
                                                                     {:y 1}
                                                                     [{:y 2}])})})])]
              (graph/query env [{:id 1} {:id 2}] [{:x [:y]}]))}

   {:title "Invalid Query In graph/query"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/query {} [:*])")
    :run   #(graph/query {} [:*])}

   {:title "Invalid Query Input"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/query {} :invalid-input [:x])")
    :run   #(graph/query {} :invalid-input [:x])}

   {:title "Missing Env"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/query {} [:x])")
    :run   #(graph/query {} [:x])}

   {:title "Conflicting Query Shape"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/query {:biff.graph/attr->resolvers {}"
                 "              :biff.graph/attr->shape {:x {:kind :scalar}}}"
                 "             [{:x [:y]}])")
    :run   #(graph/query {:biff.graph/attr->resolvers {}
                          :biff.graph/attr->shape     {:x {:kind :scalar}}}
                         [{:x [:y]}])}

   {:title "Unresolved Required Attribute"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/query {:biff.graph/attr->resolvers {}"
                 "              :biff.graph/attr->shape {:x {:kind :scalar}}}"
                 "             [:x])")
    :run   #(graph/query {:biff.graph/attr->resolvers {}
                          :biff.graph/attr->shape     {:x {:kind :scalar}}}
                         [:x])}])

(defn- trim-task-frames [s]
  (->> (str/split-lines s)
       (reduce (fn [{:keys [root-stack? trimming? lines] :as state} line]
                 (cond
                   (= line "Root stack trace:")
                   (-> state
                       (assoc :root-stack? true)
                       (update :lines conj line))

                   (and root-stack? (str/includes? line "com.biffweb.graph.tasks"))
                   (assoc state :trimming? true)

                   (and trimming? (= line ">>> error >>>"))
                   (-> state
                       (assoc :root-stack? false
                              :trimming? false)
                       (update :lines conj line))

                   trimming?
                   state

                   :else
                   (update state :lines conj line)))
               {:root-stack? false
                :trimming?   false
                :lines       []})
       :lines
       (str/join "\n")))

(defn- formatted-error [title f]
  (try
    (f)
    "No exception thrown."
    (catch Throwable t
      (trim-task-frames
       ((tel/format-signal-fn {})
        {:level   :error
         :kind    :log
         :id      :biff.graph/error-example
         :msg_    (delay title)
         :error   t
         :instant #inst "2026-01-01T00:00:00.000-00:00"
         :ns      "com.biffweb.graph"
         :?line   1})))))

(defn- markdown-for [{:keys [title code run]}]
  (str "## " title "\n\n"
       "```clojure\n"
       code
       "\n```\n\n"
       "```\n"
       (formatted-error title run)
       "\n```\n"))

(defn error-examples []
  (let [file (io/file "docs/error-examples.md")]
    (.mkdirs (.getParentFile file))
    (spit file
          (str "# Graph Error Examples\n\n"
               (str/join "\n" (map markdown-for examples))))
    (println "Generated" (.getPath file))))

(def tasks
  (assoc lib/tasks
         "error-examples" {:task 'com.biffweb.graph.tasks/error-examples
                           :doc  "Generate docs/error-examples.md"}))

(defn -main [& args]
  (apply biff.run/main* tasks args))
