(ns nimbus.lib
  #?(:cljs (:require-macros
             [nimbus.lib]))
  (:require
    [trident.util :as u]))

(defn mcat [m]
  (->> m
    (map (fn [[k v]]
           (u/prepend-keys (name k) v)))
    (apply merge)))

(defn merge-changeset [db changeset]
  (reduce (fn [db [[table id] ent]]
            (if ent
              (assoc-in db [table id] ent)
              (update db table dissoc id)))
    db
    changeset))

#?(:clj (do

(defn derivation [db-sym [defs sources] [k form]]
  (let [params (->> form
                 u/flatten-form
                 (filter #(and (symbol? %)
                            (contains? sources (keyword %))))
                 distinct
                 vec)
        args (mapv (fn [sym] `(~(keyword sym) ~db-sym)) params)]
    [(conj defs
       [k
        (if (empty? params)
          `(atom ~form)
          `(rum.derived-atom/derived-atom ~args ~k
             (fn ~params
               ~form)))])
     (conj sources k)]))

(defmacro defdb [sym & pairs]
  (let [db (gensym)]
    `(defn ~sym []
       (as-> {} ~db
         ~@(->> pairs
             (partition 2)
             (reduce (partial derivation db) [[] #{}])
             first
             (map (fn [[k form]]
                    `(assoc ~db ~k ~form))))))))

))
