(ns com.biffweb.sqlite
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.sqlite.impl.authorize :as impl.authorize]
            [com.biffweb.sqlite.impl.execute :as impl.execute]
            [com.biffweb.sqlite.impl.litestream :as impl.litestream]
            [com.biffweb.sqlite.impl.pool :as pool]
            [com.biffweb.sqlite.impl.resolver :as impl.resolver]
            [com.biffweb.sqlite.impl.schema :as impl.schema]
            [com.biffweb.sqlite.impl.sqldef :as impl.sqldef]
            [com.biffweb.sqlite.impl.system :as impl.system]))

(def ^:private column-schema
  (let [? {:optional true}]
    [:map
     [:type           [:enum :int :real :text :boolean :inst :uuid :enum :edn :blob]]
     [:primary-key  ? :boolean]
     [:unique       ? :boolean]
     [:unique-with  ? [:sequential :qualified-keyword]]
     [:required     ? :boolean]
     [:ref          ? :qualified-keyword]
     [:index        ? :boolean]
     [:extra-schema ? :any]
     [:enum-values  ? [:map-of :int :qualified-keyword]]]))

(biff.core/register
 {:biff.sqlite/columns                      [:map-of :qualified-keyword column-schema]
  :biff.sqlite/after-conn                   :any
  :biff.sqlite/authorize                    'ifn?
  :biff.sqlite/before-conn                  :any
  :biff.sqlite/bin-dir                      :string
  :biff.sqlite/db-path                      :string
  :biff.sqlite/extra-init-sql               [:sequential :string]
  :biff.sqlite/litestream-access-key-id     :string
  :biff.sqlite/litestream-bucket            :string
  :biff.sqlite/litestream-endpoint          :string
  :biff.sqlite/litestream-dir               :string
  :biff.sqlite/litestream-region            :string
  :biff.sqlite/litestream-secret-access-key :biff.core/secret
  :biff.sqlite/litestream-version           :string
  :biff.sqlite/on-tx                        'ifn?
  :biff.sqlite/read-pool                    :any
  :biff.sqlite/sqldef-version               :string
  :biff.sqlite/write-conn                   :any
  :biff.sqlite/diff                         impl.authorize/diff-schema})

(defn schema-sql [ctx]
  (impl.schema/schema-sql ctx))

(defn use-sqlite [ctx]
  (impl.system/use-sqlite ctx))

(defn use-sqldef [ctx]
  (impl.sqldef/use-sqldef ctx))

(defn use-conn [ctx]
  (pool/use-conn ctx))

(defn use-litestream [ctx]
  (impl.litestream/use-litestream ctx))

(defn execute [ctx input]
  (impl.execute/execute ctx input))


(defn module []
  (impl.system/module))

(def fx-handlers impl.system/fx-handlers)

;; ---

(defn authorized-write [ctx input]
  (impl.authorize/authorized-write ctx input))

(defn make-resolvers [ctx]
  (impl.resolver/make-resolvers ctx))
