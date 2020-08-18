(ns biff.protocols)

(defprotocol DbClient
  (get-batch! [client env])
  (get-id->before+after [client batch])
  (query-contains-doc? [client query doc])
  (batch->auth-fn-opts [client batch])
  (get-trigger-data [client env batch])
  (doc->id+doc [client doc]))
