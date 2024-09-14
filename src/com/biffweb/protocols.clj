(ns com.biffweb.protocols)

(defprotocol IndexSnapshot
  (index-get [this index-id k])
  (index-get-many [this index-id ks]
                  [this index-id-key-pairs]))
