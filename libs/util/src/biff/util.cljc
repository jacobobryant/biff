(ns biff.util
  "Utility functions with almost no dependencies.

  The only exception is clojure.tools.namespace.repl, which is used by refresh."
  (:require
    [better-cond.core :as b]
    [clojure.pprint :as pp]
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.stacktrace :as st]
    [clojure.string :as str]
    [clojure.walk :refer [postwalk]]
    [biff.util.http :as http]
    #?@(:clj [[clojure.java.shell :as shell]
              [clojure.tools.namespace.repl :as tn-repl]]
        :cljs [[cljs.core.async.impl.protocols :as async-proto]])))

#?(:clj
   (do
     (defonce system (atom nil))

     (defn refresh
       "Stops the system, refreshes source files, and restarts the system.

       The system is stopped by calling all the functions in (:biff/stop
       @biff.util/system). (:after-refresh @system) is a fully-qualified symbol which
       will be resolved and called after refreshing.

       See start-system."
       []
       (let [{:keys [biff/after-refresh biff/stop]} @system]
         (doseq [f stop]
           (println "stopping:" (pr-str f))
           (f))
         (tn-repl/refresh :after after-refresh)))

     (defn start-system
       "Starts a system from a config map and a collection of Biff components.

       Stores the system in the biff.util/system atom. See
       See https://biff.findka.com/#system-composition and refresh."
       [config components]
       (reset! system (merge {:biff/stop '()} config))
       (reduce (fn [_ f]
                 (println "starting:" (pr-str f))
                 (reset! system (f @system)))
               nil
               components)
       (println "System started."))

     (defn read-env
       "Returns a map populated from environment variables.

       Takes a collection of variable descriptors. For example, assuming the
       environment has FOO=hello and BAR=123:

       (read-env [[\"FOO\" :foo]
                  [\"BAR\" :bar #(Long/parseLong %)]
                  [\"NOT_DEFINED\" :not-defined]])
       => {:foo \"hello\"
           :bar 123}

       The third element of each descriptor, if present, will be used to coerce
       non-empty values."
       [env-keys]
       (->> env-keys
            (keep (fn [[env-key clj-key coerce]]
                    (when-some [v (not-empty (System/getenv env-key))]
                      [clj-key ((or coerce identity) v)])))
            (into {})))
     ))

(defn map-keys [f m]
  (into {} (map (fn [[k v]] [(f k) v]) m)))

(defn map-vals [f m]
  (into {} (map (fn [[k v]] [k (f v)]) m)))

(defn group-by-to [f g xs]
  (->> xs
       (group-by f)
       (map-vals g)))

(defn map-from-to [f g xs]
  (->> xs
       (map (juxt f g))
       (into {})))

(defn map-from [f xs]
  (map-from-to f identity xs))

(defn map-to [f xs]
  (map-from-to identity f xs))

(defn assoc-some [m & kvs]
  (or (some->> kvs
               (partition 2)
               (filter (comp some? second))
               (apply concat)
               not-empty
               (apply assoc m))
      m))

(defn ppr-str [x]
  (with-out-str
    (binding [*print-namespace-maps* false]
      (pp/pprint x))))

(defn pprint [x]
  (binding [*print-namespace-maps* false]
    (pp/pprint x))
  (flush))

(defn only-keys
  "Like clojure.spec.alpha/keys, but closed."
  [& {:keys [req opt req-un opt-un]}]
  (let [all-keys (->> (concat req-un opt-un)
                      (map (comp keyword name))
                      (concat req opt))]
    (s/and
      map?
      #(= % (select-keys % all-keys))
      (eval `(s/keys :req ~req :opt ~opt :req-un ~req-un :opt-un ~opt-un)))))

(defn anom-category [{:keys [cognitect.anomalies/category]}]
  (some-> category name keyword))

(defn anom->http-status [anomaly]
  (case (anom-category anomaly)
    :unavailable 503
    :interrupted 500
    :incorrect 400
    :forbidden 403
    :unsupported 400
    :not-found 404
    :conflict 409
    :fault 500
    :busy 503
    nil))

(defn anomaly? [x]
  (s/valid? (s/keys :req [:cognitect.anomalies/category]
                    :opt [:cognitect.anomalies/message])
            x))

(defn anom [category & [message & [opts]]]
  (merge opts
         {:cognitect.anomalies/category (keyword "cognitect.anomalies" (name category))}
         (when message
           {:cognitect.anomalies/message message})))

(defn throw-anom [& args]
  (let [anomaly (apply anom args)]
    (throw
      (ex-info (:cognitect.anomalies/message anomaly)
               anomaly))))

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
  (->> m
       (keep (fn [[k v]]
               (when (keyword? k)
                 [(prepend-ns ns-segment k) v])))
       (into {})))

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

(defn realize [x]
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

(defn join [sep xs]
  (butlast (interleave xs (repeat sep))))

(defn pad [n _val coll]
  (take n (concat coll (repeat _val))))

(defn between-hours? [t h1 h2]
  (let [hours (/ (mod (quot (inst-ms t) (* 1000 60)) (* 60 24)) 60.0)]
    (<= h1 hours h2)))

(defn day-of-week [t]
  (-> (inst-ms t)
      (quot (* 1000 60 60))
      (- (* 24 3) 8)
      (quot 24)
      (mod 7)))

(defn distinct-by [f coll]
  (let [step (fn step [xs seen]
               (lazy-seq
                 ((fn [[x :as xs] seen]
                    (when-let [s (seq xs)]
                      (let [fx (f x)]
                        (if (contains? seen fx)
                          (recur (rest s) seen)
                          (cons x (step (rest s) (conj seen fx)))))))
                  xs seen)))]
    (step coll #{})))

(def rfc3339 "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

#?(:clj
   (do
     (defn format-columns
       "Formats rows of text into columns.

       Example:
       ```
       (doseq [row (format-columns [[\"hellooooooooo \" \"there\"]
       [\"foo \" \"bar\"]
       [\"one column\"]])]
       (println row))
       hellooooooooo there
       foo           bar
       one column
       ```"
       [rows]
       (let [n-cols (apply max (map count rows))
             rows (map (partial pad n-cols " ") rows)
             lens (apply map (fn [& column-parts]
                               (apply max (map count column-parts)))
                         rows)
             fmt (str/join (map #(str "%" (when (not (zero? %)) (str "-" %)) "s") lens))]
         (->> rows
              (map #(apply (partial format fmt) %))
              (map str/trimr))))

     (defn print-table
       "Prints a nicely formatted table.

       Example:
       ```
       (print-table
       [[:foo \"Foo\"] [:bar \"Bar\"]]
       [{:foo 1 :bar 2} {:foo 3 :bar 4}])
       => Foo  Bar
       1    2
       3    4
       ```"
       [header-info table]
       (let [[ks header] (apply map vector header-info)
             header (map #(str % "  ") header)
             body (->> table
                       (map (apply juxt ks))
                       (map (fn [row] (map #(str % "  ") row))))
             rows (concat [header] body)]
         (doseq [row (format-columns rows)]
           (println row))))

     (defn base64-encode [bs]
       (.encodeToString (java.util.Base64/getEncoder) bs))

     (defn base64-decode [s]
       (.decode (java.util.Base64/getDecoder) s))

     (defn parse-format-date [date in-format out-format]
       (cond->> date
         in-format (.parse (new java.text.SimpleDateFormat in-format))
         out-format (.format (new java.text.SimpleDateFormat out-format))))

     (defn parse-date
       ([date]
        (parse-date date rfc3339))
       ([date in-format]
        (parse-format-date date in-format nil)))

     (defn format-date
       ([date]
        (format-date date rfc3339))
       ([date out-format]
        (parse-format-date date nil out-format)))

     (defn crop-date [d fmt]
       (-> d
           (format-date fmt)
           (parse-date fmt)))

     (defn last-midnight [t]
       (-> t
           inst-ms
           (quot (* 1000 60 60 24))
           (* 1000 60 60 24)
           (java.util.Date.)))

     (defn take-str [n s]
       (some->> s (take n) (str/join "")))

     (defn ellipsize [n s]
       (cond-> (take-str n s)
         (< n (count s)) (str "…")))

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

     (defmacro sdefs [& body]
       `(do
          ~@(for [form (partition 2 body)]
              `(s/def ~@form))))

     (defn add-deref [form syms]
       (postwalk
         #(cond->> %
            (syms %) (list deref))
         form))

     (defmacro letdelay [bindings & body]
       (let [[bindings syms] (->> bindings
                                  (partition 2)
                                  (reduce (fn [[bindings syms] [sym form]]
                                            [(into bindings [sym `(delay ~(add-deref form syms))])
                                             (conj syms sym)])
                                          [[] #{}]))]
         `(let ~bindings
            ~@(add-deref body syms))))

     (defmacro fix-print [& body]
       `(binding [*out* (alter-var-root #'*out* identity)
                  *err* (alter-var-root #'*err* identity)
                  *flush-on-newline* (alter-var-root #'*flush-on-newline* identity)]
          ~@body))

     (defmacro catchall [& body]
       `(try ~@body (catch Exception ~'_ nil)))

     (defmacro verbose [& body]
       `(try ~@body
             (catch Exception e#
               (.printStackTrace e#))))

     (defmacro pprint-ex [& body]
       `(try
          (pprint ~@body)
          (catch ~'Exception e#
            (st/print-stack-trace e#))))

     (defn parse-uuid [x]
       (catchall (java.util.UUID/fromString x)))

     (def http-registry (atom {}))

     (defmacro defhttp
       [& args]
       (let [[k & args] (if (string? (first args))
                          (conj args (ns-name *ns*))
                          args)
             [uri method params & body] args]
         `(swap! http-registry assoc-in ['~k ~uri ~method]
                 (fn [req#]
                   ((fn ~params (b/cond ~@body))
                    req#
                    (:params req#))))))

     (defmacro http-routes
       [& [k]]
       `(vec (get (deref http-registry) '~(or k (ns-name *ns*))))))

   :cljs
   (do
     (defn chan? [x]
       (satisfies? async-proto/ReadPort x))))

(def http-status->msg http/http-status->msg)

(defn wrap-wtf [handler]
  (fn [req]
    (doto (handler (doto req pprint)) pprint)))

(defn something [x]
  (when (or (not (seqable? x)) (seq x))
    x))

(defn doc-schema [req & [opt]]
  (vec
    (concat
      [:map {:closed true}
       [:xt/id]]
      (map vector req)
      (map #(vector % {:optional true}) opt))))

(defn mean [coll]
  (let [sum (apply + coll)
        count (count coll)]
    (if (pos? count)
      (/ sum count)
      0)))

(defn median [coll]
  (let [sorted (sort coll)
        cnt (count sorted)
        halfway (quot cnt 2)]
    (if (odd? cnt)
      (nth sorted halfway)
      (let [bottom (dec halfway)
            bottom-val (nth sorted bottom)
            top-val (nth sorted halfway)]
        (mean [bottom-val top-val])))))

(defn- expand-time [x]
  (if (= x :now)
    (java.util.Date.)
    x))

(defn seconds-between [t1 t2]
  (quot (- (inst-ms (expand-time t2)) (inst-ms (expand-time t1))) 1000))

(defn duration [x unit]
  (case unit
    :seconds x
    :minutes (* x 60)
    :hours (* x 60 60)
    :days (* x 60 60 24)
    :weeks (* x 60 60 24 7)))

(defn elapsed? [t1 t2 x unit]
  (< (duration x unit)
     (seconds-between t1 t2)))

; https://gist.github.com/michiakig/1093917
(defn wrand [slices]
  (let [total (reduce + slices)
        r (rand total)]
    (loop [i 0 sum 0]
      (if (< r (+ (slices i) sum))
        i
        (recur (inc i) (+ (slices i) sum))))))

(defn sample-by [f xs]
  (when (not-empty xs)
    (let [choice (wrand (mapv f xs))
          ys (concat (take choice xs) (drop (inc choice) xs))]
      (lazy-seq (cons (nth xs choice) (sample-by f ys))))))

(defn random-by [f xs]
  (when (not-empty xs)
    (let [choice (wrand (mapv f xs))]
      (lazy-seq (cons (nth xs choice) (random-by f xs))))))
