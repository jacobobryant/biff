(ns com.biffweb.experimental
  (:require [com.biffweb.impl.xtdb2 :as xt2]))

;;;; XTDB 2

(defn use-xtdb2
  "Start an XTDB node with some basic default configuration.

   Sets :biff/node (the XTDB node) and :biff/conn (a Hikari connection pool) on
   ctx. :biff/conn should generally be used for querying and submitting
   transactions.

   log:           one of #{:local :kafka} (default :local)
   storage:       one of #{:local :remote} (default :local)
   bucket,
   endpoint,
   access-key,
   secret-key:    S3 config used when storage is :remote. secret-key is accessed
                  via :biff/secret.
   hikari-config: a HikariConfig object for configuring :biff/conn. Optional.

   You can connect to the node with `psql -h localhost -p <port> -U xtdb xtdb`.
   The port number will be printed to stdout."
  [{:keys [biff/secret]
    :biff.xtdb2/keys [storage log hikari-config]
    :biff.xtdb2.storage/keys [bucket endpoint access-key secret-key]
    :or {storage :local log :local}
    :as ctx}]
  (xt2/use-xtdb2 ctx))

(defn use-xtdb2-listener
  "Polls XTDB for new transactions and passes new/updated records to :on-tx.

   The xt.txs table is polled once per second. `biffx/submit-tx` will trigger a poll immediately.
   When there's a new transaction, the given tables will be queried for records that have been
   put/patched in that transaction. Deleted records are not included. Any :on-tx functions
   registered in :biff/modules will be called once per record as `(on-tx ctx record)`.

   Records with a valid time range that doesn't intersect with the current time
   (i.e. historical or future record updates) are not included."
  [{:keys [biff/node biff/modules biff.xtdb.listener/tables] :as ctx}]
  (xt2/use-xtdb2-listener ctx))

(defn submit-tx
  "Same as xtdb.api/submit-tx, but calls `validate-tx` first.

   Also triggers a transaction log poll if use-xtdb2-listener is in use."
  [& args]
  (apply xt2/submit-tx args))

(defn validate-tx
  "Validates records in :put-docs/:patch-docs operations against the given Malli schema.

   The table keyword in each operation is used as the Malli schema and should be defined in
   `(:registry malli-opts)`. Throws an exception if there are validation failures. Otherwise returns
   true.

   For :patch-docs operations, all keys in the schema are treated as optional: this function does
   not guarantee that the resulting record will have all the required keys, only that the keys
   you've supplied are valid."
  [tx malli-opts]
  (xt2/validate-tx tx malli-opts))

(defn where-clause
  "Returns an SQL string that checks equality for the given keys.

   Example:

     (where-clause [:user/email :user/favorite-color])
     => \"user$email = ? and user$favorite_color = ?\""
  [kvs]
  (xt2/where-clause kvs))

(defn assert-unique
  "Returns SQL to assert there is at most 1 record with the given key/values in
   the table for schema..

   Example:

     (assert-unique :user {:user/email \"hello@example.com\"})
     => [\"assert 1 >= (select count(*) from users where user$email = ?\"
         \"hello@example.com\"]"
  [schema kvs]
  (xt2/assert-unique schema kvs))

(defn select-from-where [columns table kvs]
  "Returns SQL for a basic `select ... from ... where ...` query.

   Example:

     (select-from-where [:xt/id :user/joined-at] \"user\" {:user/email \"hello@example.com\"})
     => [\"select _id, user$joined_at from user where user$email = ?\" \"hello@example.com\"]"
  (xt2/select-from-where columns table kvs))

(defn prefix-uuid
  "Replaces the first two bytes in uuid-rest with those from uuid-prefix.

   This can improve locality/query performance for records that are often queried for together.
   For example, records belonging to a particular user can have a :xt/id value that's prefixed with
   the user record's :xt/id value."
  [uuid-prefix uuid-rest]
  (xt2/prefix-uuid uuid-prefix uuid-rest))

(defn tx-log
  "Returns a lazy sequence of all historical records ordered by :xt/system-from.

   tables:     a collection of tables (strings) to include in the results. If not supplied, `tx-log`
               will query for all tables in the 'public' table schema.

   after-inst: if supplied, only returns historical records with a :xt/system-from value greater
               than this.

   Records also include :xt/system-from, :xt/system-to, :xt/valid-from, and :xt/valid-to keys, as
   well as a :biff.xtdb/table key (a string)."
  [node & {:keys [tables after-inst] :as opts}]
  (xt2/tx-log node opts))

