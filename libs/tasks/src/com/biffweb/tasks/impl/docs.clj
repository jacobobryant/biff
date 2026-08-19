(ns com.biffweb.tasks.impl.docs
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [com.biffweb.tasks.impl.util :as util]))

(defn- normalize-docstring [docstring]
  (let [[first-line & rest-lines]
        (str/split-lines docstring)

        trim-width
        (if-some [widths (->> rest-lines
                              (remove str/blank?)
                              (map #(count (re-find #"^[ \t]*" %)))
                              not-empty)]
          (apply min widths)
          0)]
    (str/join
     "\n"
     (cons first-line
           (map (fn [line]
                  (if (str/blank? line)
                    ""
                    (subs line (min trim-width (count line)))))
                rest-lines)))))

(defn- namespace-resource [ns-sym]
  (let [base (->> (str/split (str ns-sym) #"\.")
                  (map #(str/replace % "-" "_"))
                  (str/join "/"))]
    (or (some #(io/resource (str base %)) [".clj" ".cljc" ".cljs"])
        (throw (ex-info "Couldn't find source file for namespace"
                        {:namespace ns-sym})))))

(defn- documented-publics [ns-sym]
  (->> (ns-publics ns-sym)
       vals
       (mapv meta)
       (filterv :doc)
       (sort-by (juxt #(or (:line %) Long/MAX_VALUE)
                      (comp str :name)))))

(defn- render-arglist [fn-name arglist]
  (str "("
       fn-name
       (when (not-empty arglist)
         (str " " (str/replace (pr-str arglist) #"(^\[|\]$)" "")))
       ")"))

(defn- signature-lines [{:keys [arglists] var-name :name}]
  (when (not-empty arglists)
    (->> arglists
         (mapv #(render-arglist var-name %))
         (str/join "\n"))))

(defn- escape-var-name [var-name]
  (str/replace (str var-name) "*" "\\*"))

(defn- var-section
  [{:keys [doc line] var-name :name :as var-meta} source-link]
  (let [signature (signature-lines var-meta)]
    (str "### "
         (escape-var-name var-name)
         "\n\n"
         "[view source]("
         (if line
           (str source-link "#L" line)
           source-link)
         ")\n\n```\n"
         (when signature
           (str signature "\n\n"))
         (normalize-docstring doc)
         "\n```")))

(defn- write-namespace-doc! [directory ns-sym]
  (require ns-sym)
  (let [ns-obj      (or (find-ns ns-sym)
                        (throw (ex-info "Couldn't load namespace"
                                        {:namespace ns-sym})))
        ns-doc      (:doc (meta ns-obj))
        resource    (namespace-resource ns-sym)
        source-file (io/file (.toURI resource))
        output-file (io/file directory (str ns-sym ".md"))
        source-link (util/relative-path (.getParentFile output-file)
                                        source-file)
        vars-meta   (documented-publics ns-sym)
        sections    (concat
                     [(str "# " ns-sym " API")]
                     (when ns-doc
                       [(str "```\n" (normalize-docstring ns-doc) "\n```")])
                     (map #(var-section % source-link) vars-meta))
        new-content (str (str/join "\n\n" sections) "\n")]
    (when (or (not (.exists output-file))
              (not= new-content (slurp output-file)))
      (io/make-parents output-file)
      (spit output-file new-content)
      (println "Generated" (.getPath output-file)))))

(defn docs []
  (let [{:biff.tasks/keys [docs-namespaces docs-directory]}
        (util/read-config {:required '[docs-namespaces]
                           :select   '[docs-directory]})]
    (run! (partial write-namespace-doc! docs-directory) docs-namespaces)))
