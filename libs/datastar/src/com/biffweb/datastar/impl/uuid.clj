(ns com.biffweb.datastar.impl.uuid
  (:import (java.nio ByteBuffer)
           (java.nio.charset StandardCharsets)
           (java.security MessageDigest)
           (java.util UUID)))

;; Adapted from clj-uuid 0.2.5 by Dan Lentz, Copyright 2024.
;; Source: https://github.com/danlentz/clj-uuid/blob/a8281b27464b07048eedfa2cc7e43479eba0628e/src/clj_uuid/core.clj#L926-L962
;; SPDX-License-Identifier: EPL-1.0

(def +namespace-dns+ #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8")

(defn v5 ^UUID [^UUID namespace-id ^String local-name]
  (let [namespace-bytes (-> (ByteBuffer/allocate 16)
                            (.putLong (.getMostSignificantBits namespace-id))
                            (.putLong (.getLeastSignificantBits namespace-id))
                            (.array))
        digest          (doto (MessageDigest/getInstance "SHA-1")
                          (.update namespace-bytes))
        bytes           (.digest digest
                                 (.getBytes local-name StandardCharsets/UTF_8))
        buffer          (ByteBuffer/wrap bytes)
        msb             (bit-or (bit-and (.getLong buffer 0) (bit-not 0xf000))
                                0x5000)
        lsb             (bit-or (bit-and (.getLong buffer 8)
                                         0x3fffffffffffffff)
                                Long/MIN_VALUE)]
    (UUID. msb lsb)))
