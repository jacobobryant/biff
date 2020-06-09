(ns biff.rules
  (:require
    [trident.util :as u]))

(defn expand-ops [rules]
  (u/map-vals
    (fn [table-rules]
      (into {}
        (for [[k v] table-rules
              k (if (coll? k) k [k])
              :let [ks (case k
                         :rw [:create
                              :get
                              :update
                              :delete
                              :query]
                         :read [:get
                                :query]
                         :write [:create
                                 :update
                                 :delete]
                         [k])]
              k ks]
          [k v])))
    rules))

(defn only-changed-keys? [doc-a doc-b & ks]
  (apply = (map (fn [{:keys [crux.db/id] :as doc}]
                  (apply dissoc (or doc {})
                    (concat ks [:crux.db/id] (when (map? id) (keys id)))))
             [doc-a doc-b])))

(defn only-changed-elements? [doc-a doc-b k & xs]
  (apply = (map #(apply (fnil disj #{}) (get % k) xs) [doc-a doc-b])))

(defn authenticated? [{:session/keys [uid]}]
  (some? uid))

(u/sdefs
  ::jwt-key string?
  ::cookie-key string?
  :biff/auth-keys (u/only-keys :opt-un [::jwt-key ::cookie-key]))

(def rules
  {:biff/auth-keys {:spec [#{:biff.auth/keys} :biff/auth-keys]}})
