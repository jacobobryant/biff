(ns biff.rum
  (:require
    [clojure.string :as str]
    [clojure.walk :refer [postwalk]]
    #?(:cljs [rum.core]))
  #?(:cljs (:require-macros biff.rum)))

#?(:clj (do
;
(defmacro defatoms [& kvs]
  `(do
     ~@(for [[k v] (partition 2 kvs)]
         `(defonce ~k (atom ~v)))))

(defn cardinality-many? [x]
  (boolean
    (some #(% x)
      [list?
       #(instance? clojure.lang.IMapEntry %)
       seq?
       #(instance? clojure.lang.IRecord %)
       coll?])))

(defn postwalk-reduce [f acc x]
  (f
   (if (cardinality-many? x)
     (reduce (partial postwalk-reduce f) acc x)
     acc)
   x))

(defn deref-form? [x]
  (and
    (list? x)
    (= 2 (count x))
    (= 'clojure.core/deref (first x))))

(defn pred-> [x f g]
  (if (f x) (g x) x))

(defmacro defderivations [& kvs]
  `(do ~@(for [[sym form] (partition 2 kvs)
               :let [deps (->> form
                            (postwalk-reduce
                              (fn [deps x]
                                (if (deref-form? x)
                                  (conj deps (second x))
                                  deps))
                              [])
                            distinct
                            vec)
                     form (postwalk #(pred-> % deref-form? second) form)
                     k (java.util.UUID/randomUUID)]]
           `(defonce ~sym (rum.core/derived-atom ~deps ~k
                            (fn ~deps ~form))))))

))
