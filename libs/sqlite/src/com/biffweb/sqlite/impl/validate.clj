(ns com.biffweb.sqlite.impl.validate
  "Internal validation of HoneySQL input against column schemas."
  (:require [malli.core :as malli]))

(defn- default-schema [col]
  (case (:type col)
    :int     :int
    :real    :double
    :text    :string
    :boolean :boolean
    :inst    inst?
    :uuid    :uuid
    :edn     [:or map? set? vector? list?]
    :blob    bytes?
    :enum    (into [:enum] (vals (:enum-values col)))))

(defn- validation-schema [col]
  (let [base     (default-schema col)
        combined (if-let [user-schema (:schema col)]
                   [:and base user-schema]
                   base)]
    (if (:required col)
      combined
      [:maybe combined])))

(def ^:private schema-for
  (memoize
   (fn [columns col-key]
     (when-let [col (first (filter #(= col-key (:id %)) columns))]
       (validation-schema col)))))

(defn- literal-value? [v]
  (cond
    (and (vector? v) (= :lift (first v))) true
    (map? v) false
    (vector? v) false
    :else true))

(defn- extract-literal [v]
  (if (and (vector? v) (= :lift (first v)))
    (second v)
    v))

(defn- validate-values! [columns kv-map]
  (doseq [[k v] kv-map
          :when (literal-value? v)]
    (when-let [schema (schema-for columns k)]
      (let [actual-val (extract-literal v)]
        (when-not (malli/validate schema actual-val)
          (throw (ex-info (str "Validation failed for column " k
                               ": value " (pr-str actual-val)
                               " does not match schema " (pr-str schema))
                          {:column k :value actual-val :schema schema})))))))

(defn validate-honeysql-input!
  "Validate INSERT/UPDATE HoneySQL input against column schemas."
  [columns input]
  (when (map? input)
    (when-let [set-map (:set input)]
      (validate-values! columns set-map))
    (when-let [values (:values input)]
      (doseq [row values]
        (validate-values! columns row)))))
