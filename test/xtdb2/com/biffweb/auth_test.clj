(ns com.biffweb.auth-test
  (:require
   [xtdb.api :as xt]
   [xtdb.node :as xtn]) 
  (:import
   [java.time Instant]))

(defn latest-snapshot-time [node]
  (some-> (xt/status node)
          :latest-completed-tx
          :system-time
          (.toInstant)))


(comment

  (def node (xtn/start-node {}))
  (.close node)

  (xt/submit-tx node
    [[:put-docs :biff.auth/code
      {:xt/id #uuid "010958e1-daab-48da-8271-c71f0d9c7359"
       :code "abc123"
       :created-at #xt/instant "2025-10-28T02:26:23.275714693Z"
       :failed-attempts 0}]])

  (xt/submit-tx node
    [[(str "update \"biff.auth\".code "
           "set failed_attempts = failed_attempts + 1 "
           "where _id = ?")
      #uuid "010958e1-daab-48da-8271-c71f0d9c7359"]])

  (xt/q node "select _id from \"biff.auth\".code")
  (xt/q node "select * from user" {:snapshot-time (latest-snapshot-time node)})

  (xt/q node (str "SELECT table_schema, table_name "
                  "FROM information_schema.tables "
                  "WHERE table_type = 'BASE TABLE' AND "
                  "table_schema NOT IN ('pg_catalog', 'information_schema');"))


  (xt/submit-tx node [["delete from user"]])

  (do
    (xt/execute-tx node [[:put-docs :user {:xt/id (random-uuid)
                                          :user/email "bob@example.com"}]])
    (xt/q node "select * from user")
    )


  (meta (with-meta node {:biff.xtdb/snapshot-time (latest-snapshot-time node)}))

  (meta node)

  (:system node)

  )


;; (defn- q [node query & [opts]]
;;   (xt/q node
;;         query
;;         (merge (:biff.xtdb/q-opts (meta node)) opts)))
;; 
;; (defn- with-snapshot-time [node snapshot-time]
;;   (with-meta node {:biff.xtdb/q-opts {:snapshot-time snapshot-time}}))
;; 
