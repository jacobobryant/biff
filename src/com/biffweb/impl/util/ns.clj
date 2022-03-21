(ns com.biffweb.impl.util.ns
  (:require [clojure.set :as set]
            [clojure.string :as str]))

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
  (->> (select-ns m ns-from)
       (map (fn [[k v]]
              (let [new-ns-parts (->> (ns-parts (namespace k))
                                      (drop (count (ns-parts ns-from)))
                                      (concat (ns-parts ns-to)))]
                [(if (empty? new-ns-parts)
                   (keyword (name k))
                   (keyword (str/join "." new-ns-parts) (name k)))
                 v])))
       (into {})))
