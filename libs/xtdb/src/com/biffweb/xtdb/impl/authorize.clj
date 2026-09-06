(ns com.biffweb.xtdb.impl.authorize
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.xtdb.impl.tx :as tx]
            [com.biffweb.xtdb.impl.util :as util]))

(defn- docs-by-id [ctx table ids]
  (if (empty? ids)
    {}
    (into {}
          (map (juxt :xt/id identity))
          (tx/q ctx {:select [:xt/id :*]
                     :from   [table]
                     :where  [:in :xt/id ids]}))))

(defn- put-diffs [ctx table docs]
  (let [before (docs-by-id ctx table (keep util/doc-id docs))]
    (mapv (fn [doc]
            (let [b (get before (util/doc-id doc))]
              {:table  table
               :op     (if b :update :create)
               :before b
               :after  doc}))
          docs)))

(defn- patch-diffs [ctx table docs]
  (let [before (docs-by-id ctx table (keep util/doc-id docs))]
    (mapv (fn [doc]
            (let [id (util/doc-id doc)
                  b  (get before id)]
              {:table  table
               :op     (if b :update :create)
               :before b
               :after  (merge b doc)}))
          docs)))

(defn- delete-diffs [ctx table ids op]
  (let [before (docs-by-id ctx table ids)]
    (mapv (fn [id]
            {:table  table
             :op     op
             :before (get before id)
             :after  nil})
          ids)))

(defmulti diff-op (fn [_ctx op] (first op)))

(defmethod diff-op :put-docs [ctx [_ table-or-opts & docs]]
  (put-diffs ctx (util/table-key table-or-opts) docs))

(defmethod diff-op :patch-docs [ctx [_ table-or-opts & docs]]
  (patch-diffs ctx (util/table-key table-or-opts) docs))

(defmethod diff-op :delete-docs [ctx [_ table-or-opts & ids]]
  (delete-diffs ctx (util/table-key table-or-opts) ids :delete))

(defmethod diff-op :erase-docs [ctx [_ table & ids]]
  (delete-diffs ctx table ids :erase))

(defmethod diff-op :default [_ctx op]
  (throw (ex-info "Operation is not supported by authorized-write." {:op op})))

(defn authorized-write
  ([ctx tx-ops]
   (authorized-write ctx tx-ops nil))
  ([ctx tx-ops tx-opts]
   (biff.core/validate ctx {:required [:biff.xtdb/authorize]})
   (let [expanded (tx/expand-ops ctx tx-ops)
         diff     (into [] (mapcat #(diff-op ctx %)) expanded)]
     (if ((:biff.xtdb/authorize ctx) (assoc ctx :biff.xtdb/diff diff) diff)
       (assoc (tx/submit-tx ctx expanded tx-opts) :biff.xtdb/diff diff)
       (throw (ex-info "Transaction was rejected by authorization rules."
                       {:biff.xtdb/diff diff}))))))
