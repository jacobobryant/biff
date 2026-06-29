(ns com.biffweb.graph.tasks.errors
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [taoensso.telemere :as tel])
  (:import [java.io PushbackReader StringReader]))

(defn- code [& lines]
  (str/join "\n" lines))

(def examples
  [{:title "Invalid Query"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/query->ast [:*])")}

   {:title "Invalid Resolver"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/resolver"
                 " {:id nil"
                 "  :input []"
                 "  :output []"
                 "  :resolve-fn (fn [_ctx _input] {})})")}

   {:title "Invalid Resolver Query"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/resolver"
                 " {:id :example/x"
                 "  :output [:*]"
                 "  :resolve-fn (fn [_ctx _input] {})})")}

   {:title "Invalid defresolver"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/defresolver invalid-defresolver"
                 "  {:batch :not-boolean}"
                 "  [_ctx _input]"
                 "  {})")}

   {:title "Resolver Returns Scalar For Join"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(def ctx"
                 "  (graph/new-ctx"
                 "   [(graph/resolver"
                 "     {:id :example/x"
                 "      :output [{:x [:y]}]"
                 "      :resolve-fn (fn [_ctx _input] {:x 1})})]))"
                 ""
                 "(graph/query ctx [{:x [:y]}])")}

   {:title "Resolver Returns Join For Scalar"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(def ctx"
                 "  (graph/new-ctx"
                 "   [(graph/resolver"
                 "     {:id :example/x"
                 "      :output [:x]"
                 "      :resolve-fn (fn [_ctx _input] {:x {:y 1}})})]))"
                 ""
                 "(graph/query ctx [:x])")}

   {:title "Resolver Returns Invalid Typed Data"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(def ctx"
                 "  (graph/new-ctx"
                 "   [(graph/resolver"
                 "     {:id :example/id"
                 "      :output [:biff.graph/id]"
                 "      :resolve-fn (fn [_ctx _input]"
                 "                    {:biff.graph/id \"not-a-keyword\"})})]))"
                 ""
                 "(graph/query ctx [:biff.graph/id])")}

   {:title "Conflicting Attribute Shapes In Ctx"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/new-ctx"
                 " [(graph/resolver"
                 "   {:id :example/scalar-x"
                 "    :output [:x]"
                 "    :resolve-fn (fn [_ctx _input] {})})"
                 "  (graph/resolver"
                 "   {:id :example/join-x"
                 "    :output [{:x [:y]}]"
                 "    :resolve-fn (fn [_ctx _input] {})})])")}

   {:title "Missing Resolver Keys"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/new-ctx"
                 " [{:biff.graph/id :example/bad"
                 "   :biff.graph/resolve-fn (fn [_ctx] {})}])")}

   {:title "Invalid Sequential Query Input"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(def ctx"
                 "  (graph/new-ctx"
                 "   [(graph/resolver"
                 "     {:id :example/x"
                 "      :output [{:x [:y]}]"
                 "      :resolve-fn (fn [_ctx _input]"
                 "                    {:x [{:y 1}]})})]))"
                 ""
                 "(graph/query ctx '({}) [{:x [:y]}])")}

   {:title "Conflicting Query Input Shape"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(def ctx"
                 "  (graph/new-ctx"
                 "   [(graph/resolver"
                 "     {:id :example/x"
                 "      :output [{:x [:y]} :z]"
                 "      :resolve-fn (fn [_ctx _input] {})})]))"
                 ""
                 "(graph/query ctx {:x 1} [:z])")}

   {:title "Resolver Throws Exception"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(def ctx"
                 "  (graph/new-ctx"
                 "   [(graph/resolver"
                 "     {:id :example/a"
                 "      :output [:a]"
                 "      :resolve-fn (fn [_ctx _input]"
                 "                    (throw (ex-info \"Boom\" {:detail 1})))})]))"
                 ""
                 "(graph/query ctx [:a])")}

   {:title "Invalid get-ctx"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/query {:biff.graph/attr->resolvers {}"
                 "              :biff.graph/attr->shape-info {}"
                 "              :biff.graph/get-ctx :not-a-function}"
                 "             [])")}

   {:title "Invalid Resolver Map In Ctx"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/query {:biff.graph/attr->resolvers"
                 "              {:x [{:biff.graph/id :example/bad"
                 "                    :biff.graph/output-ast {:x {:kind :scalar}}"
                 "                    :biff.graph/resolve-fn (fn [_ctx] {})}]}"
                 "              :biff.graph/attr->shape-info"
                 "              {:x {:biff.graph/id :example/bad"
                 "                   :biff.graph/attr :x"
                 "                   :biff.graph/attr-shape {:kind :scalar}}}}"
                 "             [:x])")}

   {:title "Conflicting Join Cardinalities"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(def ctx"
                 "  (graph/new-ctx"
                 "   [(graph/resolver"
                 "     {:id :example/x-one"
                 "      :input [:id]"
                 "      :output [{:x [:y]}]"
                 "      :resolve-fn (fn [_ctx {:keys [id]}]"
                 "                    (when (= id 1)"
                 "                      {:x {:y 1}}))})"
                 "    (graph/resolver"
                 "     {:id :example/x-many"
                 "      :input [:id]"
                 "      :output [{:x [:y]}]"
                 "      :resolve-fn (fn [_ctx {:keys [id]}]"
                 "                    (when (= id 2)"
                 "                      {:x [{:y 2}]}))})]))"
                 ""
                 "(graph/query ctx [{:id 1} {:id 2}] [{:x [:y]}])")}

   {:title "Invalid Query In graph/query"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/query {} [:*])")}

   {:title "Invalid Query Input"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/query {} :invalid-input [:x])")}

   {:title "Missing Ctx"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/query {} [:x])")}

   {:title "Conflicting Query Shape"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/query {:biff.graph/attr->resolvers {}"
                 "              :biff.graph/attr->shape-info"
                 "              {:x {:biff.graph/attr :x"
                 "                   :biff.graph/attr-shape {:kind :scalar}"
                 "                   :biff.graph/id :example/x}}}"
                 "             [{:x [:y]}])")}

   {:title "Unresolved Required Attribute"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(graph/query {:biff.graph/attr->resolvers {}"
                 "              :biff.graph/attr->shape-info"
                 "              {:x {:biff.graph/attr :x"
                 "                   :biff.graph/attr-shape {:kind :scalar}}}}"
                 "             [:x])")}

   {:title "Nested Unresolved Required Attribute"
    :code  (code "(require '[com.biffweb.graph :as graph])"
                 ""
                 "(def ctx"
                 "  (graph/new-ctx"
                 "   [(graph/resolver"
                 "     {:id :example/b"
                 "      :output [{:b [:seed]}]"
                 "      :resolve-fn (fn [_ctx _input] {})})"
                 "    (graph/resolver"
                 "     {:id :example/d"
                 "      :input [:g]"
                 "      :output [{:d [:ok]}]"
                 "      :resolve-fn (fn [_ctx _input]"
                 "                    {:d {:ok true}})})]))"
                 ""
                 "(graph/query ctx {:b {:seed true}} [{:b [{:d [:ok]}]}])")}])

(def ^:private eof (Object.))

(defn- read-forms [s]
  (with-open [reader (PushbackReader. (StringReader. s))]
    (loop [forms []]
      (let [form (read {:eof eof} reader)]
        (if (identical? eof form)
          forms
          (recur (conj forms form)))))))

(defn- eval-code [s]
  (let [ns-sym (symbol (str "com.biffweb.graph.error-example." (gensym)))
        ns     (create-ns ns-sym)]
    (try
      (binding [*ns* ns]
        (refer 'clojure.core)
        (reduce (fn [_ form]
                  (eval form))
                nil
                (read-forms s)))
      (finally
        (remove-ns ns-sym)))))

(defn- trim-task-frames [s]
  (->> (str/split-lines s)
       (reduce (fn [{:keys [root-stack? trimming?] :as state} line]
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

(defn- wrap-line [line]
  (let [limit 78]
    (loop [lines []
           line  line]
      (if (<= (count line) limit)
        (str/join "\n" (conj lines line))
        (let [idx (or (some (fn [i]
                              (when (= \space (nth line i))
                                i))
                            (range (min limit (dec (count line))) 0 -1))
                      limit)]
          (recur (conj lines (subs line 0 idx))
                 (str "  " (str/triml (subs line idx)))))))))

(defn- wrap-root-lines [s]
  (->> (str/split-lines s)
       (map #(if (str/starts-with? % "Root: ")
               (wrap-line %)
               %))
       (str/join "\n")))

(defn- formatted-error [title code]
  (try
    (eval-code code)
    "No exception thrown."
    (catch Throwable t
      (-> ((tel/format-signal-fn {})
           {:level   :error
            :kind    :log
            :id      :biff.graph/error-example
            :msg_    (delay title)
            :error   t
            :instant #inst "2026-01-01T00:00:00.000-00:00"
            :ns      "com.biffweb.graph"
            :?line   1})
          trim-task-frames
          wrap-root-lines))))

(defn- split-root-stack-trace [s]
  (let [lines          (str/split-lines s)
        [body stack]   (split-with #(not= "Root stack trace:" %) lines)
        formatted-body (str/join "\n" (reverse (drop-while str/blank? (reverse body))))]
    (if (seq stack)
      [formatted-body (str/join "\n" stack)]
      [s nil])))

(defn- markdown-for [{:keys [title code]}]
  (let [[body stack] (split-root-stack-trace (formatted-error title code))]
    (str "## " title "\n\n"
         "```clojure\n"
         code
         "\n```\n\n"
         "```\n"
         body
         "\n```\n"
         (when stack
           (str "\n<details>\n"
                "<summary>Root stack trace</summary>\n\n"
                "```\n"
                stack
                "\n```\n"
                "</details>\n")))))

(defn error-examples []
  (let [file (io/file "docs/error-examples.md")]
    (.mkdirs (.getParentFile file))
    (spit file
          (str "# Graph Error Examples\n\n"
               (str/join "\n" (map markdown-for examples))))
    (println "Generated" (.getPath file))))
