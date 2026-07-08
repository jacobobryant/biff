(ns com.biffweb.sqlite.impl.kv
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.fx :as fx]
            [com.biffweb.sqlite.impl.execute :as impl.exec]
            [taoensso.nippy :as nippy]))

(defn- uuid7 []
  (first (fx/uuid7 (.nextLong (java.util.Random.))
                   (java.time.Instant/now))))

(def columns
  {:biff-sqlite-kv/id        {:type :uuid :primary-key true}
   :biff-sqlite-kv/namespace {:type        :text
                              :required    true
                              :unique-with [:biff-sqlite-kv/k]}
   :biff-sqlite-kv/k         {:type :text :required true}
   :biff-sqlite-kv/v         {:type :blob :required true}})

(defn set-value [ctx namespace* key* value]
  (biff.core/validate {:biff.core/kv-namespace namespace*
                       :biff.core/kv-key key*})
  (if (nil? value)
    (impl.exec/execute
     ctx
     {:delete-from :biff-sqlite-kv
      :where [:and
              [:= :biff-sqlite-kv/namespace (str namespace*)]
              [:= :biff-sqlite-kv/k key*]]})
    (let [value* (nippy/fast-freeze value)]
      (impl.exec/execute
       ctx
       {:insert-into   :biff-sqlite-kv
        :values        [{:biff-sqlite-kv/id        (uuid7)
                         :biff-sqlite-kv/namespace (str namespace*)
                         :biff-sqlite-kv/k         key*
                         :biff-sqlite-kv/v         value*}]
        :on-conflict   [:biff-sqlite-kv/namespace :biff-sqlite-kv/k]
        :do-update-set {:biff-sqlite-kv/v value*}})))
  nil)

(defn get-value [ctx namespace* key*]
  (biff.core/validate {:biff.core/kv-namespace namespace*
                       :biff.core/kv-key key*})
  (some-> (impl.exec/execute
           ctx
           {:select [:biff-sqlite-kv/v]
            :from   :biff-sqlite-kv
            :where  [:and
                     [:= :biff-sqlite-kv/namespace (str namespace*)]
                     [:= :biff-sqlite-kv/k key*]]})
          first
          :biff-sqlite-kv/v
          nippy/fast-thaw))

(defn list-keys
  ([ctx namespace*]
   (list-keys ctx namespace* nil))
  ([ctx namespace* key-prefix]
   (biff.core/validate {:biff.core/kv-namespace namespace*
                        :biff.core/kv-prefix key-prefix})
   (let [where [:= :biff-sqlite-kv/namespace (str namespace*)]
         where (if key-prefix
                 [:and
                  where
                  [:like :biff-sqlite-kv/k (str key-prefix "%")]]
                 where)]
     (->> (impl.exec/execute
           ctx
           {:select   [:biff-sqlite-kv/k]
            :from     :biff-sqlite-kv
            :where    where
            :order-by [[:biff-sqlite-kv/k :asc]]})
          (mapv :biff-sqlite-kv/k)))))
