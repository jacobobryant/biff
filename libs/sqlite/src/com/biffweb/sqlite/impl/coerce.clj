(ns com.biffweb.sqlite.impl.coerce
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [next.jdbc.result-set :as rs]
            [taoensso.nippy :as nippy])
  (:import [java.nio ByteBuffer]
           [java.time Instant]
           [java.util UUID]
           [java.sql ResultSet ResultSetMetaData]))

;;;; read coercion -------------------------------------------------------------

(defn- column-names [^ResultSetMetaData rsmeta]
  (mapv (fn [^Integer i]
          (let [label (.getColumnLabel rsmeta i)]
            (if (str/includes? label "/")
              (keyword (csk/->kebab-case-string label))
              (if-some [table (not-empty (.getTableName rsmeta i))]
                (keyword (csk/->kebab-case-string table)
                         (csk/->kebab-case-string label))
                (keyword (csk/->kebab-case-string label))))))
        (range 1 (inc (.getColumnCount rsmeta)))))

;; like rs/as-kebab-maps but preserves namespaces from qualified aliases
;; (aliases with a "/" in them). It's important that users be able to use
;; qualified keywords as aliases so that coercing-column-reader can infer what
;; the type of the column is supposed to be.
(defn- as-qualified-alias-kebab-maps [^ResultSet rs _opts]
  (let [rsmeta (.getMetaData rs)
        cols   (column-names rsmeta)]
    (rs/->MapResultSetBuilder rs rsmeta cols)))

(defn- bytes->uuid [^bytes ba]
  (let [bb (ByteBuffer/wrap ba)]
    (UUID. (.getLong bb) (.getLong bb))))

(defn- epoch-ms->inst [ms]
  (Instant/ofEpochMilli ms))

(defn- int->bool [n]
  (case n
    0 false
    1 true
    (throw (ex-info "Invalid boolean value, expected 0 or 1" {:value n}))))

(defn- make-enum-reader [enum-map]
  (fn [db-val]
    (or (get enum-map db-val)
        (throw (ex-info "Invalid enum value"
                        {:enum-value db-val :available (keys enum-map)})))))

(defn- read-coercers [columns]
  (into {}
        (keep (fn [[id {:keys [enum-values] column-type :type}]]
                (when-some [coerce-fn (case column-type
                                        :uuid bytes->uuid
                                        :inst epoch-ms->inst
                                        :boolean int->bool
                                        :enum (make-enum-reader enum-values)
                                        :edn nippy/fast-thaw
                                        nil)]
                  [id coerce-fn])))
        columns))

(defn- coercing-column-reader [columns]
  (let [col->coerce-fn (read-coercers columns)]
    (fn [builder ^ResultSet rs ^Integer i]
      (let [col-kw        (nth (:cols builder) (dec i))
            coerce-fn     (get col->coerce-fn col-kw)
            value         (.getObject rs i)
            coerced-value (if (and coerce-fn (some? value))
                            (coerce-fn value)
                            value)]
        (rs/read-column-by-index coerced-value (:rsmeta builder) i)))))

(def builder-fn
  (memoize
   (fn [columns]
     (let [column-reader (coercing-column-reader columns)]
       (rs/builder-adapter as-qualified-alias-kebab-maps column-reader)))))

;;;; write coercion ------------------------------------------------------------

(defn- uuid->bytes [^UUID uuid]
  (let [bb (ByteBuffer/allocate 16)]
    (.putLong bb (.getMostSignificantBits uuid))
    (.putLong bb (.getLeastSignificantBits uuid))
    (.array bb)))

(defn- build-enum-val->int [columns]
  (into {}
        (comp (map val)
              (mapcat :enum-values)
              (map (fn [[k v]]
                     [v k])))
        columns))

(def ^:private memo-build-enum-val->int (memoize build-enum-val->int))

(defn coerce-params [columns params]
  (let [enum-val->int (memo-build-enum-val->int columns)]
    (mapv (fn [v]
            (cond
              (uuid? v)    (uuid->bytes v)
              (inst? v)    (inst-ms v)
              (boolean? v) (if v 1 0)
              (keyword? v) (or (get enum-val->int v)
                               (throw (ex-info
                                       "Unknown enum keyword value"
                                       {:value     v
                                        :available (keys enum-val->int)})))
              (coll? v)    (nippy/fast-freeze v)
              :else        v))
          params)))
