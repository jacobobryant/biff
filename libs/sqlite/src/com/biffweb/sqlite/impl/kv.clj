(ns com.biffweb.sqlite.impl.kv
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.fx :as fx]
            [com.biffweb.sqlite.impl.execute :as exec]
            [taoensso.nippy :as nippy]))

(defn- uuid7 []
  (first (fx/uuid7 (.nextLong (java.util.Random.))
                   (java.time.Instant/now))))

(def columns
  {:biff-sqlite-kv/id        {:type :uuid :primary-key true}
   :biff-sqlite-kv/namespace {:type        :text
                              :required    true
                              :unique-with [:biff-sqlite-kv/k]}
   :biff-sqlite-kv/k      {:type :text :required true}
   :biff-sqlite-kv/v    {:type :blob :required true}})

(defn set-value [ctx namespace* key* value]
  (biff.core/validate {:biff.core/kv-namespace namespace*
                       :biff.core/kv-key key*})
  (if (nil? value)
    (exec/execute ctx ["DELETE FROM biff_sqlite_kv WHERE namespace = ? AND k = ?"
                       (str namespace*)
                       key*])
    (let [value* (nippy/fast-freeze value)]
      (exec/execute ctx {:insert-into   :biff-sqlite-kv
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
  (some-> (exec/execute ctx {:select [:biff-sqlite-kv/v]
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
   (->> (exec/execute
         ctx
         (cond-> {:select   [:biff-sqlite-kv/k]
                  :from     :biff-sqlite-kv
                  :where    [:= :biff-sqlite-kv/namespace (str namespace*)]
                  :order-by [[:biff-sqlite-kv/k :asc]]}
           key-prefix
           (update :where
                   (fn [where]
                     [:and
                      where
                      [:like :biff-sqlite-kv/k (str key-prefix "%")]]))))
        (mapv :biff-sqlite-kv/k))))
