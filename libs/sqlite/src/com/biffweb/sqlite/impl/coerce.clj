(ns com.biffweb.sqlite.impl.coerce
  "Internal type coercion between Clojure values and SQLite storage types."
  (:require [com.biffweb.sqlite.impl.query :as query]
            [com.biffweb.sqlite.impl.util :as util]
            [next.jdbc.result-set :as rs]
            [taoensso.nippy :as nippy])
  (:import [java.nio ByteBuffer]
           [java.time Instant]
           [java.util UUID]))

;; --- Read coercions (DB -> Clojure) ---

(defn bytes->uuid [^bytes ba]
  (when ba
    (let [bb (ByteBuffer/wrap ba)]
      (UUID. (.getLong bb) (.getLong bb)))))

(defn epoch-ms->inst [ms]
  (when ms (Instant/ofEpochMilli ms)))

(defn int->bool [n]
  (when (some? n)
    (case n
      0 false
      1 true
      (throw (ex-info "Invalid boolean value, expected 0 or 1" {:value n})))))

(defn make-enum-reader [enum-map]
  (fn [db-val]
    (when (some? db-val)
      (or (get enum-map db-val)
          (throw (ex-info "Unknown enum value"
                          {:value db-val :available-values enum-map}))))))

;; --- Write coercions (Clojure -> DB) ---

(defn uuid->bytes [^UUID uuid]
  (let [bb (ByteBuffer/allocate 16)]
    (.putLong bb (.getMostSignificantBits uuid))
    (.putLong bb (.getLeastSignificantBits uuid))
    (.array bb)))

(defn inst->epoch-ms [x]
  (when x (.toEpochMilli ^Instant x)))

(defn bool->int [b]
  (if b 1 0))

;; --- Coercion dispatch ---

(defn thaw
  "Temporary until Yakread's data is migrated to fast-freeze"
  [bytes*]
  (if (= [78 80 89] (take 3 bytes*))
    (nippy/thaw bytes*)
    (nippy/fast-thaw bytes*)))

(defn build-all-read-coercions
  "Build read coercions indexed by SQL column name strings.
   Includes both 'table.column' and 'column' entries."
  [columns]
  (into {}
        (keep (fn [{:keys [id enum-values] column-type :type}]
                (when-some [coerce-fn (case column-type
                                        :uuid bytes->uuid
                                        :inst epoch-ms->inst
                                        :boolean int->bool
                                        :enum (make-enum-reader enum-values)
                                        :edn thaw
                                        nil)]
                  [id coerce-fn])))
        columns))

(defn build-enum-val->int
  "Build a map from namespaced enum keywords to their integer DB values."
  [columns]
  (into {}
        (mapcat (fn [col]
                  (when-let [enum-map (:enum-values col)]
                    (let [col-ns (str (namespace (:id col)) "." (name (:id col)))]
                      (map (fn [[idx kw]]
                             (when-not (and (keyword? kw)
                                            (= col-ns (namespace kw)))
                               (throw (ex-info (str "Enum values must be namespaced keywords "
                                                    "with namespace matching the column. "
                                                    "Expected namespace: " col-ns
                                                    ", got: " (pr-str kw))
                                               {:column (:id col) :value kw})))
                             [kw idx])
                           enum-map)))))
        columns))

(defn coerce-params
  "Coerce SQL parameter values based on their types."
  [enum-val->int params]
  (mapv (fn [v]
          (cond
            (uuid? v)    (uuid->bytes v)
            (inst? v)    (inst->epoch-ms v)
            (boolean? v) (bool->int v)
            (keyword? v) (if-let [n (get enum-val->int v)]
                           n
                           (throw (ex-info "Unknown enum keyword value"
                                           {:value     v
                                            :available (keys enum-val->int)})))
            (map? v)     (nippy/fast-freeze v)
            (vector? v)  (nippy/fast-freeze v)
            (list? v)    (nippy/fast-freeze v)
            (set? v)     (nippy/fast-freeze v)
            :else        v))
        params))

(def memoized-coercions
  "Memoized function that builds coercion data from a columns map.
   Returns {:builder-fn ... :enum-val->int ... :normalized-columns ...}."
  (memoize
   (fn [columns]
     (let [cols           (util/normalize-columns columns)
           read-coercions (build-all-read-coercions cols)
           column-reader  (query/make-column-reader read-coercions)
           enum-val->int  (build-enum-val->int cols)]
       {:builder-fn         (rs/builder-adapter query/smart-kebab-maps column-reader)
        :enum-val->int      enum-val->int
        :normalized-columns cols}))))
