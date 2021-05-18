(ns biff.util.protocols)

(defprotocol Schema
  "See https://biff.findka.com/#authorization-rules"
  (valid? [this doc-type doc])
  (explain-human [this doc-type doc])
  (assert-valid [this doc-type doc])
  (doc-type [this doc]))
