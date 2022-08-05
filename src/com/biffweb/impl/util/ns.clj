(ns com.biffweb.impl.util.ns
  (:require [clojure.string :as str]))

(defn ns-parts [nspace]
  (if (empty? (str nspace))
    []
    (str/split (str nspace) #"\.")))

(defn select-ns [m nspace]
  (let [parts (ns-parts nspace)]
    (->> (keys m)
         (filter (fn [k]
                   (= parts (take (count parts) (ns-parts (namespace k))))))
         (select-keys m))))

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
