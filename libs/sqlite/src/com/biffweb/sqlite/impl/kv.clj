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
                              :unique-with [:biff-sqlite-kv/key-]}
   :biff-sqlite-kv/key-      {:type :text :required true}
   :biff-sqlite-kv/value-    {:type :blob :required true}})

(defn set-value [ctx namespace* key* value]
  (biff.core/validate {:biff.core/kv-namespace namespace*
                       :biff.core/kv-key key*})
  (if (nil? value)
    (exec/execute ctx ["DELETE FROM biff_sqlite_kv WHERE namespace = ? AND key_ = ?"
                       namespace*
                       key*])
    (let [value* (nippy/fast-freeze value)]
      (exec/execute ctx {:insert-into   :biff-sqlite-kv
                         :values        [{:biff-sqlite-kv/id        (uuid7)
                                          :biff-sqlite-kv/namespace namespace*
                                          :biff-sqlite-kv/key-      key*
                                          :biff-sqlite-kv/value-    value*}]
                         :on-conflict   [:biff-sqlite-kv/namespace :biff-sqlite-kv/key-]
                         :do-update-set {:biff-sqlite-kv/value- value*}})))
  nil)

(defn get-value [ctx namespace* key*]
  (biff.core/validate {:biff.core/kv-namespace namespace*
                       :biff.core/kv-key key*})
  (some-> (exec/execute ctx {:select [:biff-sqlite-kv/value-]
                             :from   :biff-sqlite-kv
                             :where  [:and
                                      [:= :biff-sqlite-kv/namespace namespace*]
                                      [:= :biff-sqlite-kv/key- key*]]})
          first
          :biff-sqlite-kv/value-
          nippy/fast-thaw))

(defn list-keys
  ([ctx namespace*]
   (list-keys ctx namespace* nil))
  ([ctx namespace* key-prefix]
   (biff.core/validate {:biff.core/kv-namespace namespace*
                        :biff.core/kv-prefix key-prefix})
   (->> (exec/execute
         ctx
         (cond-> {:select   [:biff-sqlite-kv/key-]
                  :from     :biff-sqlite-kv
                  :where    [:= :biff-sqlite-kv/namespace (str namespace*)]
                  :order-by [[:biff-sqlite-kv/key- :asc]]}
           key-prefix
           (update :where
                   (fn [where]
                     [:and
                      where
                      [:like :biff-sqlite-kv/key- (str key-prefix "%")]]))))
        (mapv :biff-sqlite-kv/key-))))
