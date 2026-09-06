(ns com.biffweb.xtdb.impl.kv
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.xtdb.impl.tx :as tx]
            [taoensso.nippy :as nippy]))

(def columns
  {:biff-xtdb-kv/namespace {}
   :biff-xtdb-kv/key       {}
   :biff-xtdb-kv/value     {}})

(defn- kv-id [namespace* key*]
  (str namespace* "\n" key*))

(defn set-value [ctx namespace* key* value]
  (biff.core/validate {:biff.core/kv-namespace namespace*
                       :biff.core/kv-key       key*})
  (if (nil? value)
    (tx/execute-tx ctx [[:delete-docs :biff-xtdb-kv (kv-id namespace* key*)]])
    (tx/execute-tx ctx [[:put-docs :biff-xtdb-kv
                         {:xt/id                  (kv-id namespace* key*)
                          :biff-xtdb-kv/namespace (str namespace*)
                          :biff-xtdb-kv/key       key*
                          :biff-xtdb-kv/value     (nippy/fast-freeze value)}]]))
  nil)

(defn get-value [ctx namespace* key*]
  (biff.core/validate {:biff.core/kv-namespace namespace*
                       :biff.core/kv-key       key*})
  (some-> (tx/q ctx {:select [:biff-xtdb-kv/value]
                     :from   [:biff-xtdb-kv]
                     :where  [:= :xt/id (kv-id namespace* key*)]})
          first
          :biff-xtdb-kv/value
          nippy/fast-thaw))

(defn list-keys
  ([ctx namespace*]
   (list-keys ctx namespace* nil))
  ([ctx namespace* key-prefix]
   (biff.core/validate {:biff.core/kv-namespace namespace*
                        :biff.core/kv-prefix    key-prefix})
   (let [where [:= :biff-xtdb-kv/namespace (str namespace*)]
         where (if key-prefix
                 [:and
                  where
                  [:like :biff-xtdb-kv/key (str key-prefix "%")]]
                 where)]
     (mapv :biff-xtdb-kv/key
           (tx/q ctx {:select   [:biff-xtdb-kv/key]
                      :from     [:biff-xtdb-kv]
                      :where    where
                      :order-by [[:biff-xtdb-kv/key :asc]]})))))
