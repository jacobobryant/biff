(ns tasks.profile-ns
  (:require [clojure.java.io :as io]))

(defn- loaded-lib? [prefix lib options]
  (let [lib    (if prefix (symbol (str prefix \. lib)) lib)
        opts   (apply hash-map options)
        loaded @(var-get (ns-resolve 'clojure.core '*loaded-libs*))]
    (and (contains? loaded lib)
         (not (:reload opts))
         (not (:reload-all opts)))))

(defn- source [lib]
  (let [base (-> (str lib)
                 (.replace "." "/")
                 (.replace "-" "_"))]
    (or (some (fn [extension]
                (let [path (str base extension)]
                  (when-some [resource (io/resource path)]
                    {:path path :resource resource})))
              [".clj" ".cljc"])
        (throw (ex-info "Namespace source not found" {:namespace lib})))))

(defn- form-label [form]
  (if (seq? form)
    (let [[op form-name] form]
      (str op (when (symbol? form-name) (str " " form-name))))
    (let [text (pr-str form)]
      (if (> (count text) 60)
        (str (subs text 0 57) "...")
        text))))

(defn- column-width [heading values]
  (reduce max (count heading) (map count values)))

(defn- format-ms [value]
  (format "%.1f" value))

(defn- print-profile [lib entries]
  (let [totals         {:elapsed (reduce + 0 (map :elapsed entries))
                        :require (reduce + 0 (map :require entries))
                        :self    (reduce + 0 (map :self entries))}
        elapsed-values (map format-ms
                            (conj (mapv :elapsed entries) (:elapsed totals)))
        require-values (map format-ms
                            (conj (mapv :require entries) (:require totals)))
        self-values    (map format-ms
                            (conj (mapv :self entries) (:self totals)))
        line-values    (map (comp str :line) entries)
        widths         {:elapsed (column-width "elapsed ms" elapsed-values)
                        :require (column-width "require ms" require-values)
                        :self    (column-width "self ms" self-values)
                        :line    (column-width "line" line-values)}
        row-format     (str "%" (:elapsed widths) "s  %"
                            (:require widths) "s  %"
                            (:self widths) "s  %" (:line widths) "s  %s\n")]
    (println)
    (println "Namespace profile:" lib)
    (println)
    (printf row-format "elapsed ms" "require ms" "self ms" "line" "form")
    (doseq [{:keys [elapsed require self line label]} entries]
      (printf row-format
              (format-ms elapsed)
              (format-ms require)
              (format-ms self)
              (str line)
              label))
    (printf row-format
            (format-ms (:elapsed totals))
            (format-ms (:require totals))
            (format-ms (:self totals))
            ""
            "total")))

(defn profile-main [lib]
  (let [{:keys [path resource]} (source lib)
        require-time            (atom 0.0)
        depth                   (ThreadLocal.)
        load-var                (ns-resolve 'clojure.core 'load-lib)
        load-lib                (var-get load-var)
        eof                     (Object.)]
    (alter-var-root
     load-var
     (constantly
      (fn [prefix lib & options]
        (if (loaded-lib? prefix lib options)
          (apply load-lib prefix lib options)
          (let [current-depth (or (.get depth) 0)
                start         (System/nanoTime)]
            (.set depth (inc current-depth))
            (try
              (apply load-lib prefix lib options)
              (finally
                (when (zero? current-depth)
                  (swap! require-time +
                         (/ (- (System/nanoTime) start) 1000000.0)))
                (.set depth current-depth))))))))
    (try
      (with-open [reader (io/reader resource)]
        (let [reader (clojure.lang.LineNumberingPushbackReader. reader)]
          (binding [*file* path
                    *ns*   (the-ns 'user)]
            (loop [entries []]
              (let [line           (.getLineNumber reader)
                    require-before @require-time
                    start          (System/nanoTime)
                    form           (read {:eof      eof     :read-cond :allow
                                          :features #{:clj}}
                                         reader)]
                (if (identical? eof form)
                  (print-profile lib entries)
                  (do
                    (eval form)
                    (let [elapsed  (/ (- (System/nanoTime) start) 1000000.0)
                          required (- @require-time require-before)]
                      (recur
                       (conj entries
                             {:elapsed elapsed
                              :require required
                              :self    (- elapsed required)
                              :line    (or (:line (meta form)) line)
                              :label   (form-label form)}))))))))))
      (finally
        (alter-var-root load-var (constantly load-lib))))))

(defn profile-ns [namespace]
  (let [lib       (symbol namespace)
        main-opts ["-i" "dev/tasks/profile_ns.clj"
                   "-e" (str "(tasks.profile-ns/profile-main '" lib ")")]
        deps      (pr-str {:aliases {:biff/profile-ns {:main-opts main-opts}}})
        command   ["clj" "-Sdeps" deps "-M:run:biff/profile-ns"]
        process   (-> (ProcessBuilder. command)
                      .inheritIO
                      .start)
        exit      (.waitFor process)]
    (when-not (zero? exit)
      (throw (ex-info "Namespace profiling failed"
                      {:exit exit :namespace lib})))
    nil))
