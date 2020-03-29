(ns nimbus.lib
  (:require-macros
    [nimbus.lib]))

(defn merge-changeset [db changeset]
  (reduce (fn [db [[table id] ent]]
            (if ent
              (assoc-in db [table id] ent)
              (update db table dissoc id)))
    db
    changeset))
