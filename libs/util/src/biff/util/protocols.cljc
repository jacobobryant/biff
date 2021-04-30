(ns biff.util.protocols)

(defprotocol Schema
  (valid? [this doc-type doc])
  (explain-human [this doc-type doc])
  (assert-valid [this doc-type doc])
  (doc-type [this doc]))
