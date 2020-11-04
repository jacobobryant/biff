(ns biff.util
  (:require
    [clojure.pprint :as pp]
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.walk :refer [postwalk]]
    #?@(:cljs [[cljs.core.async]]
         :clj [[clojure.java.shell :as shell]])))

(defn map-kv [f m]
  (into {} (map (fn [[k v]] (f k v)) m)))

(defn map-keys [f m]
  (map-kv (fn [k v] [(f k) v]) m))

(defn map-vals [f m]
  (map-kv (fn [k v] [k (f v)]) m))

(defn map-from-to [f g xs]
  (->> xs
       (map (juxt f g))
       (into {})))

(defn map-from [f xs]
  (map-from-to f identity xs))

(defn map-to [f xs]
  (map-from-to identity f xs))

(defn pprint [x]
  (binding [*print-namespace-maps* false]
    (pp/pprint x)))

(defn ppr-str [x]
  (with-out-str (pprint x)))

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

(defn prepend-ns [ns-segment k]
  (keyword
    (cond-> ns-segment
      (not-empty (namespace k)) (str "." (namespace k)))
    (name k)))

(defn prepend-keys [ns-segment m]
  (map-keys #(prepend-ns ns-segment %) m))

(defn nest-string-keys [m ks]
  (let [ks (set ks)]
    (reduce (fn [resp [k v]]
              (let [nested-k (keyword (namespace k))]
                (if (ks nested-k)
                  (-> resp
                    (update nested-k assoc (name k) v)
                    (dissoc k))
                  resp)))
    m
    m)))

(defn add-seconds [date seconds]
  #?(:clj (java.util.Date/from (.plusSeconds (.toInstant date) seconds))
     :cljs (js/Date. (+ (.getTime date) (* 1000 seconds)))))

(defn concrete [x]
  (cond
    (var? x) @x
    (fn? x) (x)
    :default x))

(defn split-by [pred xs]
  (reduce #(update %1 (if (pred %2) 0 1) (fnil conj []) %2)
    [nil nil] xs))

(defn compare= [x y]
  (= 0 (compare x y)))

(defn compare< [x y]
  (= -1 (compare x y)))

(defn compare> [x y]
  (= 1 (compare x y)))

(defn compare<= [x y]
  (or (compare< x y) (compare= x y)))

(defn compare>= [x y]
  (or (compare> x y) (compare= x y)))

#?(:clj
   (do
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

     (defmacro fix-stdout [& forms]
       `(let [ret# (atom nil)
              s# (with-out-str
                   (reset! ret# (do ~@forms)))]
          (some->> s#
            not-empty
            (.print System/out))
          @ret#))


     (defn add-deref [form syms]
       (postwalk
         #(cond->> %
            (syms %) (list deref))
         form))

     (defmacro letdelay [bindings & forms]
       (let [[bindings syms] (->> bindings
                               (partition 2)
                               (reduce (fn [[bindings syms] [sym form]]
                                         [(into bindings [sym `(delay ~(add-deref form syms))])
                                          (conj syms sym)])
                                 [[] #{}]))]
         `(let ~bindings
            ~@(add-deref forms syms))))

     (defmacro catchall [& forms]
       `(try ~@forms (catch Exception ~'_ nil))))

   :cljs
   (do
     (defn chan? [x]
       (satisfies? cljs.core.async.impl.protocols/ReadPort x))))
