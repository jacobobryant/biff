(ns com.biffweb.sqlite.impl.resolver
  (:require [clojure.string :as str]
            [com.biffweb.graph :as biff.graph]
            [com.biffweb.sqlite.impl.execute :as exec]))

(defn- strip-id-suffix [k]
  (keyword (namespace k) (subs (name k) 0 (- (count (name k)) 3))))

(defn make-resolvers [{:biff.sqlite/keys [columns]}]
  (let [columns  (or columns {})
        by-table (group-by (comp keyword namespace key) columns)]
    (vec
     (for [[table-key table-cols] by-table
           :let [table-cols-map (into {} table-cols)
                 pk-entry       (first (filter (fn [[_ props]] (:primary-key props))
                                               table-cols-map))
                 _              (when-not pk-entry
                                  (throw (ex-info (str "No primary key found for table " table-key)
                                                  {:table table-key})))
                 pk-key         (key pk-entry)
                 non-pk-cols    (dissoc table-cols-map pk-key)
                 ref-cols       (into {}
                                      (keep (fn [[col-key props]]
                                              (when (and (:ref props)
                                                         (str/ends-with? (name col-key) "-id"))
                                                [col-key (:ref props)])))
                                      non-pk-cols)
                 join-mappings  (mapv (fn [[col-key ref-key]]
                                         {:join-key (strip-id-suffix col-key)
                                          :col-key  col-key
                                          :ref-key  ref-key})
                                       ref-cols)
                 output         (vec (concat (keys non-pk-cols)
                                             (map (fn [{:keys [join-key ref-key]}]
                                                    {join-key [ref-key]})
                                                  join-mappings)))
                 resolver-id    (keyword "com.biffweb.sqlite"
                                         (str (name table-key) "-resolver"))]]
       (biff.graph/resolver
        {:id         resolver-id
         :input      [pk-key]
         :output     output
         :batch      true
         :resolve-fn (fn [ctx inputs]
                       (let [ids         (mapv pk-key inputs)
                             results     (exec/execute ctx {:select :*
                                                            :from   table-key
                                                            :where  [:in pk-key ids]})
                             process-row (fn [row]
                                           (let [row (dissoc row pk-key)
                                                 row (reduce
                                                      (fn [row {:keys [join-key col-key ref-key]}]
                                                        (let [ref-val (get row col-key)]
                                                          (cond-> row
                                                            (some? ref-val) (assoc join-key {ref-key ref-val}))))
                                                      row
                                                      join-mappings)]
                                             (into {} (filter (fn [[_ v]] (some? v))) row)))
                             id->result  (into {} (map (juxt pk-key process-row)) results)]
                         (mapv (fn [input]
                                 (get id->result (get input pk-key) {}))
                               inputs)))})))))
