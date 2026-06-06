(ns com.biffweb.core.impl.validation
  (:require [clojure.string :as str]
            [malli.core :as malli]
            [malli.error :as malli.e]
            [malli.registry :as malli.r]))

(defonce ^:dynamic *registry* (atom {}))

(defn register
  [schemas]
  (swap! *registry* merge schemas)
  nil)

(defn get-registry []
  @*registry*)

(defn assertion-error [& message-parts]
  (throw (AssertionError. (str/join "" message-parts))))

(defn- humanize-explanation [explanation]
  (let [message (malli.e/humanize explanation)]
    (cond
      (malli/validate [:tuple :string] message) (first message)
      (string? message) message
      (nil? message) (pr-str explanation)
      :else (pr-str message))))

(defn- truncate-str [s n]
  (if (<= (count s) n)
    s
    (str (subs s 0 (dec n)) "…")))

(defn- value-str [x]
  (truncate-str (pr-str x) 50))

(defn- validate-map [m {:keys [required biff-registry malli-registry]}]
  (when-some [missing (not-empty (remove #(contains? m %) required))]
    (assertion-error "Missing required key"
                     (when (< 1 (count missing)) "s")
                     ": "
                     (str/join ", " (mapv pr-str missing))))
  (doseq [[k v] (select-keys m (keys biff-registry))
          :when (not (malli/validate k v {:registry malli-registry}))
          :let  [explanation (malli/explain k v {:registry malli-registry})
                 message     (humanize-explanation explanation)]]
    (assertion-error "`" (pr-str k) " " (value-str v) "` is invalid: " message)))

(defn validate*
  [m-or-seq & {:keys [extra-schema] :as opts}]
  (let [biff-registry  (merge (get-registry) extra-schema)
        malli-registry (malli.r/composite-registry
                        malli/default-registry
                        (malli.r/fast-registry biff-registry))
        opts           (merge opts {:biff-registry  biff-registry
                                    :malli-registry malli-registry})]
    (doseq [m (if (sequential? m-or-seq) m-or-seq [m-or-seq])]
      (cond
        (nil? m) nil
        (map? m) (validate-map m opts)
        :else (assertion-error "Expected a map, got " (value-str m)))))
  m-or-seq)

(defmacro validate
  [m-or-seq & opts]
  (if *assert*
    `(validate* ~m-or-seq ~@opts)
    m-or-seq))
