(ns com.biffweb.protocols)

(defprotocol IndexDatasource
  (index-get [this index-id k])
  (index-get-many [this index-id ks]
                  [this index-id-key-pairs]))
