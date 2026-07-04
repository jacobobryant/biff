(ns com.biffweb.sqlite.impl.kv
  (:require [com.biffweb.sqlite.impl.execute :as exec]
            [taoensso.nippy :as nippy]))

(def columns
  {:biff-sqlite-kv/id        {:type :uuid :primary-key true}
   :biff-sqlite-kv/namespace {:type        :text
                              :required    true
                              :unique-with [:biff-sqlite-kv/key-]}
   :biff-sqlite-kv/key-      {:type :text :required true}
   :biff-sqlite-kv/value-    {:type :blob :required true}})

(defn- validate-args [namespace key]
  (assert (qualified-keyword? namespace) "namespace must be a qualified keyword")
  (assert (string? key) "key must be a string"))

(defn- validate-prefix-args [namespace key-prefix]
  (assert (qualified-keyword? namespace) "namespace must be a qualified keyword")
  (assert (or (nil? key-prefix) (string? key-prefix))
          "key-prefix must be nil or a string"))

(defn set-value [ctx namespace key value]
  (validate-args namespace key)
  (if (nil? value)
    (do
      (exec/execute ctx ["DELETE FROM biff_sqlite_kv WHERE namespace = ? AND key_ = ?"
                         (str namespace)
                         key])
      nil)
    (let [value* (nippy/fast-freeze value)]
      (exec/execute ctx {:insert-into   :biff-sqlite-kv
                         :values        [{:biff-sqlite-kv/id        (random-uuid)
                                          :biff-sqlite-kv/namespace (str namespace)
                                          :biff-sqlite-kv/key-      key
                                          :biff-sqlite-kv/value-    value*}]
                         :on-conflict   [:biff-sqlite-kv/namespace :biff-sqlite-kv/key-]
                         :do-update-set {:biff-sqlite-kv/value- value*}})
      nil)))

(defn get-value [ctx namespace key]
  (validate-args namespace key)
  (some-> (first (exec/execute ctx {:select [:biff-sqlite-kv/value-]
                                    :from   :biff-sqlite-kv
                                    :where  [:and
                                             [:= :biff-sqlite-kv/namespace (str namespace)]
                                             [:= :biff-sqlite-kv/key- key]]}))
          :biff-sqlite-kv/value-
          nippy/fast-thaw))

(defn list-keys
  ([ctx namespace]
   (list-keys ctx namespace nil))
  ([ctx namespace key-prefix]
   (validate-prefix-args namespace key-prefix)
   (->> (exec/execute
         ctx
         (cond-> {:select   [:biff-sqlite-kv/key-]
                  :from     :biff-sqlite-kv
                  :where    [:= :biff-sqlite-kv/namespace (str namespace)]
                  :order-by [[:biff-sqlite-kv/key- :asc]]}
           key-prefix
           (update :where
                   (fn [where]
                     [:and
                      where
                      [:like :biff-sqlite-kv/key- (str key-prefix "%")]]))))
        (mapv :biff-sqlite-kv/key-))))
