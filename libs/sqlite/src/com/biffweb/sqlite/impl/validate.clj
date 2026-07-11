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
          :when (literal-value? v)
          :let  [schema (schema-for columns k)]
          :when schema
          :let  [value (extract-literal v)]
          :when (not (malli/validate schema value))]
    (throw (ex-info (str "Invalid value for " k)
                    {:column k :value value :schema schema}))))

;; best-effort attempt at ensuring that :set / :values conforms to the schema /
;; types in `columns`.
(defn validate-schema-on-write [columns input]
  (when-let [set-map (:set input)]
    (validate-values! columns set-map))
  (when-let [values (:values input)]
    (doseq [row values]
      (validate-values! columns row))))
