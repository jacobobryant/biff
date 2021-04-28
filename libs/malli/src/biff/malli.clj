(ns biff.malli
  (:refer-clojure :exclude [assert])
  (:require
    [biff.util-tmp :as bu]
    [malli.core :as m]
    [malli.error :as me]
    [malli.registry :as mr]))

(defn assert [schema x opts]
  (when-not (m/validate schema x opts)
    (throw
      (ex-info "Invalid schema."
        {:value x
         :schema schema
         :explain (me/humanize (m/explain schema x opts))}))))

(defn debug [schema x opts]
  (if (m/validate schema x opts)
    true
    (do
      (println (me/humanize (m/explain schema x opts)))
      false)))

(defn registry [reg]
  (mr/composite-registry m/default-registry reg))
