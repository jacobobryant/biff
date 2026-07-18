(ns com.biffweb.xtdb
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.xtdb.impl.authorize :as impl.authorize]
            [com.biffweb.xtdb.impl.resolver :as impl.resolver]
            [com.biffweb.xtdb.impl.system :as impl.system]
            [com.biffweb.xtdb.impl.tx :as impl.tx]))

(biff.core/register
 {:biff.xtdb/authorize               'ifn?
  :biff.xtdb/columns                 [:map-of :qualified-keyword
                                      [:map
                                       [:schema {:optional true} :any]
                                       [:ref {:optional true} :qualified-keyword]]]
  :biff.xtdb/config                  :any
  :biff.xtdb/diff                    [:vector
                                      [:map
                                       [:table :keyword]
                                       [:op [:enum :create :update :delete :erase]]
                                       [:before [:maybe [:map-of :keyword :any]]]
                                       [:after [:maybe [:map-of :keyword :any]]]]]
  :biff.xtdb/latest-system-time      inst?
  :biff.xtdb/log                     [:enum :memory :local :kafka]
  :biff.xtdb/log-bootstrap-servers   :string
  :biff.xtdb/log-epoch               :int
  :biff.xtdb/log-topic               :string
  :biff.xtdb/node                    :any
  :biff.xtdb/poll-now                'ifn?
  :biff.xtdb/snapshot-token          :string
  :biff.xtdb/storage                 [:enum :memory :local :remote]
  :biff.xtdb/storage-access-key      :string
  :biff.xtdb/storage-bucket          :string
  :biff.xtdb/storage-endpoint        :string
  :biff.xtdb/storage-max-cache-bytes :int
  :biff.xtdb/storage-secret-key      :biff.core/secret})

(def expand-config impl.system/expand-config)
(def use-xtdb impl.system/use-xtdb)
(def q impl.tx/q)
(def execute-tx impl.tx/execute-tx)
(def submit-tx impl.tx/submit-tx)
(def authorized-write impl.authorize/authorized-write)
(def prefix-uuid impl.tx/prefix-uuid)
(def columns->schema impl.resolver/columns->schema)
(def make-resolvers impl.resolver/make-resolvers)
(def fx-handlers impl.system/fx-handlers)
(def module impl.system/module)
