(ns com.biffweb.sqlite.impl.resolver
  (:require [clojure.string :as str]
            [com.biffweb.core :as biff.core]
            [com.biffweb.graph :as biff.graph]
            [com.biffweb.sqlite.impl.execute :as exec]))

(defn- strip-id-suffix [k]
  (keyword (namespace k)
           (str/replace (name k) #"-id$" "")))

;; This is used both for generating the resolver's :output query and for
;; formatting the actual resolver output.
(defn- make-output-mappings [col]
  (cond
    (:primary-key col)
    nil

    (and (:ref col)
         (str/ends-with? (name (:id col)) "-id"))
    [{:source-key (:id col)
      :output-key (:id col)}
     {:source-key  (:id col)
      :output-key  (strip-id-suffix (:id col))
      :foreign-key (:ref col)}]

    :else
    [{:source-key  (:id col)
      :output-key  (:id col)
      :foreign-key (:ref col)}]))

(defn make-resolvers [columns]
  (biff.core/validate {:biff.sqlite/columns columns})
  (let [columns (mapv (fn [[id opts]]
                        (assoc opts
                               :id id
                               :table (keyword (namespace id))))
                      columns)]
    (vec
     (for [[table-key columns] (group-by :table columns)

           :let  [primary-key (->> columns
                                   (filterv :primary-key)
                                   first
                                   :id)]
           :when primary-key
           :let  [output-mappings (mapcat make-output-mappings columns)

                  process-row
                  (fn [row]
                    (into {}
                          (keep (fn [{:keys [source-key
                                             output-key
                                             foreign-key]}]
                                  (when-some [value (get row source-key)]
                                    [output-key
                                     (if foreign-key
                                       {foreign-key value}
                                       value)])))
                          output-mappings))]]
       (biff.graph/resolver
        {:id     (keyword "com.biffweb.sqlite"
                          (str (name table-key) "-resolver"))
         :input  [primary-key]
         :output (mapv (fn [{:keys [output-key foreign-key]}]
                         (if foreign-key
                           {output-key [foreign-key]}
                           output-key))
                       output-mappings)
         :batch  true

         :resolve-fn
         (fn [ctx inputs]
           (let [ids        (mapv primary-key inputs)
                 results    (exec/execute ctx {:select :*
                                               :from   table-key
                                               :where  [:in primary-key ids]})
                 id->result (into {}
                                  (map (juxt primary-key process-row))
                                  results)]
             (mapv (fn [input]
                     (get id->result (get input primary-key) {}))
                   inputs)))})))))
