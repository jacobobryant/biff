(ns com.biffweb.xtdb.impl.resolver
  (:require [clojure.string :as str]
            [com.biffweb.core :as biff.core]
            [com.biffweb.graph :as biff.graph]
            [com.biffweb.xtdb.impl.tx :as tx]
            [com.biffweb.xtdb.impl.util :as util]))

(defn- strip-id-suffix [k]
  (keyword (namespace k)
           (str/replace (name k) #"-id$" "")))

(defn- output-mapping [{:keys [id ref] :as _col}]
  (if (and ref (str/ends-with? (name id) "-id"))
    [{:source-key id
      :output-key id}
     {:source-key  id
      :output-key  (strip-id-suffix id)
      :foreign-key ref}]
    [{:source-key  id
      :output-key  id
      :foreign-key ref}]))

(defn make-resolvers [columns]
  (biff.core/validate {:biff.xtdb/columns columns})
  (let [columns (mapv (fn [[id opts]]
                        (assoc opts
                               :id id
                               :table (util/table-from-column id)))
                      columns)]
    (vec
     (for [[table columns] (group-by :table columns)

           :let [primary-key (util/table-id-key table)
                 columns     (remove #(= primary-key (:id %)) columns)
                 mappings    (mapcat output-mapping columns)
                 process-row (fn [row]
                               (into {}
                                     (keep (fn [{:keys [source-key output-key foreign-key]}]
                                             (when-some [value (get row source-key)]
                                               [output-key
                                                (if foreign-key
                                                  {foreign-key value}
                                                  value)])))
                                     mappings))]]
       (biff.graph/resolver
        {:id     (keyword "com.biffweb.xtdb" (str (name table) "-resolver"))
         :input  [primary-key]
         :output (mapv (fn [{:keys [output-key foreign-key]}]
                         (if foreign-key
                           {output-key [foreign-key]}
                           output-key))
                       mappings)
         :batch  true
         :resolve-fn
         (fn [ctx inputs]
           (let [ids     (mapv primary-key inputs)
                 rows    (tx/q ctx {:select (into [:xt/id]
                                                  (map :id)
                                                  columns)
                                    :from   [table]
                                    :where  [:in :xt/id ids]})
                 id->row (into {}
                               (map (fn [row]
                                      [(:xt/id row)
                                       (process-row (assoc row primary-key (:xt/id row)))]))
                               rows)]
             (mapv (fn [input]
                     (get id->row (get input primary-key) {}))
                   inputs)))})))))

(defn columns->schema [columns]
  (into {}
        (keep (fn [[k {:keys [schema]}]]
                (when schema
                  [k schema])))
        columns))
