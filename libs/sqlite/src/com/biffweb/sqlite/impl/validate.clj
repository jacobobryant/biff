(ns com.biffweb.sqlite.impl.validate
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
        combined (if-some [user-schema (:extra-schema col)]
                   [:and base user-schema]
                   base)]
    (if (or (:required col) (:primary-key col))
      combined
      [:maybe combined])))

(def ^:private schema-for
  (memoize
   (fn [columns col-key]
     (some-> (get columns col-key)
             validation-schema))))

(defn- literal-value? [v]
  (or (and (vector? v) (= :lift (first v)))
      (not (or (map? v) (vector? v)))))

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

(defn validate-write
  [columns input]
  (when-let [set-map (:set input)]
    (validate-values! columns set-map))
  (when-let [values (:values input)]
    (doseq [row values]
      (validate-values! columns row))))
