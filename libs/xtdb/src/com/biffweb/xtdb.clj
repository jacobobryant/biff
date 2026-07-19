(ns com.biffweb.xtdb
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.xtdb.impl.authorize :as impl.authorize]
            [com.biffweb.xtdb.impl.resolver :as impl.resolver]
            [com.biffweb.xtdb.impl.system :as impl.system]
            [com.biffweb.xtdb.impl.tx :as impl.tx]))

(biff.core/register
 {:biff.xtdb/authorize              'ifn?
  :biff.xtdb/columns                [:map-of :qualified-keyword
                                     [:map
                                      [:schema {:optional true} :any]
                                      [:ref {:optional true} :qualified-keyword]]]
  :biff.xtdb/config                 :any
  :biff.xtdb/connection-pool        :any
  :biff.xtdb/disk-cache-max-bytes   [:maybe :int]
  :biff.xtdb/hikari-config          :any
  :biff.xtdb/diff                   [:vector
                                     [:map
                                      [:table :keyword]
                                      [:op [:enum :create :update :delete :erase]]
                                      [:before [:maybe [:map-of :keyword :any]]]
                                      [:after [:maybe [:map-of :keyword :any]]]]]
  :biff.xtdb/latest-system-time     inst?
  :biff.xtdb/log                    [:enum :memory :local :kafka]
  :biff.xtdb/log-bootstrap-servers  :string
  :biff.xtdb/log-epoch              :int
  :biff.xtdb/log-topic              :string
  :biff.xtdb/memory-cache-max-bytes [:maybe :int]
  :biff.xtdb/node                   :any
  :biff.xtdb/poll-now               'ifn?
  :biff.xtdb/snapshot-token         :string
  :biff.xtdb/storage                [:enum :memory :local :remote]
  :biff.xtdb/storage-access-key     :string
  :biff.xtdb/storage-bucket         :string
  :biff.xtdb/storage-endpoint       :string
  :biff.xtdb/storage-secret-key     :biff.core/secret})

(defn expand-config
  "Returns an XTDB node config map.

   If `:biff.xtdb/config` is set, returns it as-is. Otherwise, returns a config
   map based on `storage` and `log` plus their related options.

   `storage` may be `:memory`, `:local`, or `:remote`. `log` may be `:memory`,
   `:local`, or `:kafka`. Both default to `:local`."
  {:arglists '([{:biff.xtdb/keys [config
                                  storage
                                  log
                                  storage-bucket
                                  storage-endpoint
                                  storage-access-key
                                  storage-secret-key
                                  disk-cache-max-bytes
                                  memory-cache-max-bytes
                                  log-bootstrap-servers
                                  log-topic
                                  log-epoch]}])}
  [ctx]
  (impl.system/expand-config ctx))

(defn use-xtdb
  "Starts an in-process XTDB node and a connection pool.

   Passes ctx to expand-config. When ctx is passed to q, execute-tx, submit-tx,
   or authorized-write, those functions:

   - Use the connection pool.
   - Trigger a call to :biff.core/on-tx, if set.

   Sets :biff.xtdb/node and :biff.xtdb/connection-pool on ctx."
  [ctx]
  (impl.system/use-xtdb ctx))

(defn q
  "Wrapper for xtdb.api/q.

   If query is a map, formats it with HoneySQL, adding support for qualified
   keywords (e.g. :user/email gets converted to :user$email).

   Includes snapshot-token in the query opts."
  {:arglists '([{:biff.xtdb/keys [connection-pool node snapshot-token] :as ctx} query]
               [{:biff.xtdb/keys [connection-pool node snapshot-token] :as ctx} query opts])}
  ([ctx query]
   (impl.tx/q ctx query))
  ([ctx query opts]
   (impl.tx/q ctx query opts)))

(defn execute-tx
  "Wrapper for xtdb.api/execute-tx.

   - Supports Biff's custom transaction operations.
   - Enforces Malli schema for :put-docs and :patch-docs operations via
     biff.core/validate.
   - Calls :biff.core/on-tx if set.

   Returns a map with :tx-id and :system-time."
  {:arglists '([ctx tx-ops]
               [{:biff.xtdb/keys [node connection-pool] :as ctx}
                tx-ops
                tx-opts])}
  ([ctx tx-ops]
   (impl.tx/execute-tx ctx tx-ops))
  ([ctx tx-ops tx-opts]
   (impl.tx/execute-tx ctx tx-ops tx-opts)))

(defn submit-tx
  "Wrapper for xtdb.api/submit-tx

   - Supports Biff's custom transaction operations.
   - Enforces Malli schema for :put-docs and :patch-docs operations via
     biff.core/validate.
   - Calls :biff.core/on-tx if set.

   Returns a map with :tx-id."
  {:arglists '([ctx tx-ops]
               [{:biff.xtdb/keys [node connection-pool] :as ctx}
                tx-ops tx-opts])}
  ([ctx tx-ops]
   (impl.tx/submit-tx ctx tx-ops))
  ([ctx tx-ops tx-opts]
   (impl.tx/submit-tx ctx tx-ops tx-opts)))

(defn authorized-write
  "Wrapper for xt/submit-tx that enforces authorization rules.

   The :biff.xtdb/authorize function must be set. If it doesn't return true,
   the transaction is rejected.

   Only :put-docs, :patch-docs, :delete-docs, and :erase-docs operations are
   accepted.

   On success, returns the result of submit-tx with :biff.xtdb/diff added."
  {:arglists '([{:biff.xtdb/keys [authorize node] :as ctx} tx-ops]
               [{:biff.xtdb/keys [authorize node] :as ctx} tx-ops tx-opts])}
  ([ctx tx-ops]
   (impl.authorize/authorized-write ctx tx-ops))
  ([ctx tx-ops tx-opts]
   (impl.authorize/authorized-write ctx tx-ops tx-opts)))

(defn prefix-uuid
  "Returns a UUID made from the first four characters of `uuid-prefix` and the
   rest of `uuid-rest`."
  [uuid-prefix uuid-rest]
  (impl.tx/prefix-uuid uuid-prefix uuid-rest))

(defn columns->schema
  "Returns a biff.core schema registry map for `columns`.

   For each entry in `columns` that has a `:schema` value, the column keyword is
   mapped to that schema. This is useful with `biff.core/register` before
   calling execute-tx or submit-tx."
  [columns]
  (impl.resolver/columns->schema columns))

(defn make-resolvers
  "Returns a sequence of biff.graph resolvers, one for each table.

   Each resolver takes an alias of :xt/id as input, which has the form
   :<table>/id (e.g. :user/id). All other keys in `columns` with the same
   namespace are included in the output.

   Columns with `:ref` are returned as joins. If a ref column ends in `-id`,
   that column is returned as a regular non-join attribute and an additional
   join attribute without the `-id` suffix is also returned:

     :user/pet-id {:ref :pet/id, ...}
     ;; ->
     :output [:user/pet-id
              {:user/pet [:pet/id]}]

   All resolvers have `:batch true`."
  [columns]
  (impl.resolver/make-resolvers columns))

(def
  ^{:doc "A biff.fx handlers map. Contains :biff.xtdb.fx/execute-tx,
          :biff.xtdb.fx/submit-tx, and :biff.xtdb.fx/authorized-write."}
  fx-handlers
  impl.system/fx-handlers)

(defn module
  "Returns a biff.core module.

   - provides :biff.fx/handlers in the module
   - provides key-value store functions in the system map:
     :biff.core/kv-get, :biff.core/kv-set, :biff.core/kv-list.
   - provides :biff.core/wrap-db-snapshot in the system map."
  []
  (impl.system/module))
