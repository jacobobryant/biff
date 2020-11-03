(ns biff.util
  (:require
    [clojure.java.shell :as shell]
    [clojure.pprint :as pp]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.spec.alpha :as s]))

(defn map-kv [f m]
  (into {} (map (fn [[k v]] (f k v)) m)))

(defn map-keys [f m]
  (map-kv (fn [k v] [(f k) v]) m))

(defn map-vals [f m]
  (map-kv (fn [k v] [k (f v)]) m))

(defn pprint [x]
  (binding [*print-namespace-maps* false]
    (pp/pprint x)))

(defn ppr-str [x]
  (with-out-str (pprint x)))

(defn sppit [f x]
  (spit f (ppr-str x)))

(defn sh
  "Runs a shell command.

  Returns the output if successful; otherwise, throws an exception."
  [& args]
  (let [result (apply shell/sh args)]
    (if (= 0 (:exit result))
      (:out result)
      (throw (ex-info (:err result) result)))))

(defmacro sdefs [& forms]
  `(do
     ~@(for [form (partition 2 forms)]
         `(s/def ~@form))))

(defn only-keys [& {:keys [req opt req-un opt-un]}]
  (let [all-keys (->> (concat req-un opt-un)
                   (map (comp keyword name))
                   (concat req opt))]
    (s/and
      map?
      #(= % (select-keys % all-keys))
      (eval `(s/keys :req ~req :opt ~opt :req-un ~req-un :opt-un ~opt-un)))))

(defn assoc-pred
  "Like assoc, but skip kv pairs where (f v) is false."
  [m f & kvs]
  (if-some [kvs (some->> kvs
                         (partition 2)
                         (filter (comp f second))
                         (apply concat)
                         not-empty)]
    (apply assoc m kvs)
    m))

(defn assoc-some [m & kvs]
  (apply assoc-pred m some? kvs))

(defn anomaly? [x]
  (s/valid? (s/keys :req [:cognitect.anomalies/category] :opt [:cognitect.anomalies/message]) x))

(defn anom [category & [message & kvs]]
  (apply assoc-some
    {:cognitect.anomalies/category (keyword "cognitect.anomalies" (name category))}
    :cognitect.anomalies/message message
    kvs))

(defn ns-contains? [nspace sym]
  (and (namespace sym)
    (let [segments (str/split (name nspace) #"\.")]
      (= segments (take (count segments) (str/split (namespace sym) #"\."))))))

(defn select-as [m key-map]
  (-> m
    (select-keys (keys key-map))
    (set/rename-keys key-map)))

(defn select-ns [m nspace]
  (select-keys m (filter #(ns-contains? nspace (symbol %)) (keys m))))

(defn ns-parts [nspace]
  (if (nil? nspace)
    []
    (some-> nspace
      str
      not-empty
      (str/split #"\."))))

(defn select-ns-as [m ns-from ns-to]
  (map-keys
    (fn [k]
      (let [new-ns-parts (->> (ns-parts (namespace k))
                           (drop (count (ns-parts ns-from)))
                           (concat (ns-parts ns-to)))]
        (if (empty? new-ns-parts)
          (keyword (name k))
          (keyword (str/join "." new-ns-parts) (name k)))))
    (select-ns m ns-from)))
