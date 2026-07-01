(ns com.biffweb.tasks.docs
  "Generates API docs from namespace and var docstrings."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [com.biffweb.tasks.util :as util]))

(defn- normalize-docstring [docstring]
  (let [[first-line & rest-lines] (str/split-lines docstring)
        trim-width                (if-some [widths (->> rest-lines
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
       (keep (fn [v]
               (let [{:keys [arglists doc line name]} (meta v)]
                 (when doc
                   {:arglists arglists
                    :doc      doc
                    :line     line
                    :name     name}))))
       (sort-by (juxt #(or (:line %) Long/MAX_VALUE)
                      (comp str :name)))))

(defn- relative-path [from-dir to-file]
  (-> (.relativize (.toPath (.getCanonicalFile from-dir))
                   (.toPath (.getCanonicalFile to-file)))
      str
      (str/replace "\\" "/")))

(defn- anchor-id [name]
  (-> (str name)
      str/lower-case
      (str/replace #"[^a-z0-9-]" "")
      (str/replace #"(^-+|-+$)" "")))

(defn- heading-line [line]
  (when-some [[_ hashes title] (re-matches #"^(#+)\s+(.+?)\s*$" line)]
    {:level (count hashes)
     :title title}))

(defn- table-of-contents [markdown]
  (str/join
   "\n"
   (let [headings (keep heading-line (str/split-lines markdown))
         min-level (apply min (map :level headings))]
     (for [{:keys [level title]} headings]
       (str (apply str (repeat (* 2 (- level min-level)) " "))
            "- ["
            title
            "](#"
            (anchor-id title)
            ")")))))

(defn render-arglist [fn-name arglist]
  (str "("
       fn-name
       (when (not-empty arglist)
         (str " "
              (subs (pr-str arglist)
                    1
                    (dec (count (pr-str arglist))))))
       ")"))

(defn- signature-lines [{:keys [arglists name]}]
  (when (not-empty arglists)
    (->> arglists
         (mapv #(render-arglist name %))
         (str/join "\n"))))

(defn- var-section [{:keys [doc line name] :as var-meta} source-link]
  (let [signature (signature-lines var-meta)
        body      (if signature
                    (str signature "\n\n" (normalize-docstring doc))
                    (normalize-docstring doc))]
    (str "### "
         name
         "\n\n"
         "[view source]("
         (if line
           (str source-link "#L" line)
           source-link)
         ")\n\n```\n"
         body
         "\n```")))

(defn- write-namespace-doc! [ns-sym]
  (require ns-sym)
  (let [ns-obj      (or (find-ns ns-sym)
                        (throw (ex-info "Couldn't load namespace"
                                        {:namespace ns-sym})))
        ns-doc      (:doc (meta ns-obj))
        resource    (namespace-resource ns-sym)
        source-file (io/file (.toURI resource))
        output-file (io/file (System/getProperty "user.dir")
                             "docs"
                             "api"
                             (str ns-sym ".md"))
        source-link (relative-path (.getParentFile output-file) source-file)
        vars        (documented-publics ns-sym)]
    (when-not ns-doc
      (throw (ex-info "Namespace is missing a docstring"
                      {:namespace ns-sym})))
    (let [body     (str/join "\n\n"
                             (concat
                              [(normalize-docstring ns-doc)
                               "## API"]
                              (map #(var-section % source-link) vars)))
          sections [(str "# "
                         ns-sym
                         " reference"
                         "\n\n"
                         (table-of-contents body))
                    body]]
      (io/make-parents output-file)
      (spit output-file (str (str/join "\n\n" sections) "\n"))
      (println "Generated" (.getPath output-file)))))

(defn docs
  "Generates markdown API docs under docs/api/."
  []
  (let [namespaces (:biff.tasks/docs-namespaces (util/read-config))]
    (when-not namespaces
      (throw (ex-info ":biff.tasks/docs-namespaces must be set for the docs task." {})))
    (run! write-namespace-doc! namespaces)))
