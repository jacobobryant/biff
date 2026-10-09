(ns com.biffweb.sqlite.impl.resolver
  (:require [clojure.string :as str]
            [com.biffweb.core :as biff.core]
            [com.biffweb.graph :as biff.graph]
            [com.biffweb.sqlite.impl.execute :as exec]
            [malli.core :as malli]))

(defn- strip-id-suffix [k]
  (keyword (namespace k)
           (str/replace (name k) #"-id$" "")))

(defn- schema-shape [schema]
  (let [schema   (malli/schema schema)
        type     (malli/type schema)
        children (malli/children schema)]
    (cond
      (#{:map :map-of 'map?} type)
      :map

      (#{:vector :sequential} type)
      (when (= :map (schema-shape (first children)))
        :join)

      (= :tuple type)
      (when (every? #(= :map (schema-shape %)) children)
        :join)

      (= :maybe type)
      (schema-shape (first children))

      (= :and type)
      (first (keep schema-shape children))

      (= :or type)
      (let [shapes (mapv schema-shape children)]
        (when (and (seq shapes) (every? some? shapes))
          (if (apply = shapes)
            (first shapes)
            :join))))))

(defn- edn-join? [col]
  (and (= :edn (:type col))
       (some? (:extra-schema col))
       (try
         (some? (schema-shape (:extra-schema col)))
         (catch Exception _
           false))))

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
      :foreign-key (:ref col)
      :edn-join    (edn-join? col)}]))

(defn- primary-keys [columns]
  (into {}
        (keep (fn [[table cols]]
                (when-some [id (some (fn [col]
                                       (when (:primary-key col) (:id col)))
                                     cols)]
                  [table id])))
        (group-by :table columns)))

(defn- unique-resolver [col primary-key input-keys composite?]
  (let [{:keys [id table]} col]
    (biff.graph/resolver
     {:id     (keyword "com.biffweb.sqlite"
                       (str (namespace id) "-" (name id)
                            (if composite?
                              "-unique-with-resolver"
                              "-unique-resolver")))
      :input  input-keys
      :output [primary-key]
      :batch  true

      :resolve-fn
      (fn [ctx inputs]
        (let [where   (into [:or]
                            (map (fn [input]
                                   (into [:and]
                                         (map (fn [key]
                                                [:= key (get input key)]))
                                         input-keys)))
                            inputs)
              rows    (exec/execute ctx {:select (conj input-keys primary-key)
                                         :from   table
                                         :where  where})
              key->id (into {}
                            (map (fn [row]
                                   [(mapv row input-keys)
                                    (get row primary-key)]))
                            rows)]
          (mapv (fn [input]
                  (if-some [value (get key->id (mapv input input-keys))]
                    {primary-key value}
                    {}))
                inputs)))})))

(defn- backref-resolver [col primary-key output-key many?]
  (let [{:keys [id table ref]} col]
    (biff.graph/resolver
     {:id     (keyword "com.biffweb.sqlite"
                       (str (namespace id) "-" (name id) "-"
                            (if many? "backref-many" "backref") "-resolver"))
      :input  [ref]
      :output [{output-key [primary-key]}]
      :batch  true

      :resolve-fn
      (fn [ctx inputs]
        (let [rows       (exec/execute ctx {:select [id primary-key]
                                            :from   table
                                            :where  [:in id (mapv ref inputs)]})
              id->rows  (group-by id rows)]
          (mapv (fn [input]
                  {output-key
                   (let [matches (mapv #(array-map primary-key (get % primary-key))
                                       (get id->rows (get input ref)))]
                     (if many? matches (or (first matches) {})))})
                inputs)))})))

(defn make-resolvers [columns]
  (biff.core/validate {:biff.sqlite/columns columns})
  (let [columns (mapv (fn [[id opts]]
                        (assoc opts
                               :id id
                               :table (keyword (namespace id))))
                      columns)
        key-by-table (primary-keys columns)]
    (vec
     (concat
      (for [[table-key columns] (group-by :table columns)

           :let  [primary-key (get key-by-table table-key)]
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
         :output (mapv (fn [{:keys [output-key foreign-key edn-join]}]
                         (cond
                           foreign-key {output-key [foreign-key]}
                           edn-join {output-key [:*]}
                           :else output-key))
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
                   inputs)))}))
      (for [col columns
            :let [primary-key (get key-by-table (:table col))]
            :when primary-key
            [input-keys composite?]
            (concat (when (:unique col) [[[(:id col)] false]])
                    (when (:unique-with col)
                      [[(into [(:id col)] (:unique-with col)) true]]))]
        (unique-resolver col primary-key input-keys composite?))
      (for [col columns
            [key many?] [[:backref false] [:backref-many true]]
            :let [output-key (get col key)]
            :when output-key]
        (let [primary-key (get key-by-table (:table col))]
          (when-not primary-key
            (throw (ex-info (str key " is set but " (:table col)
                                 " has no primary key")
                            {:column col})))
          (when-not (:ref col)
            (throw (ex-info (str key " is set but :ref is not") {:column col})))
          (backref-resolver col primary-key output-key many?)))))))
