(ns hello.rules
  (:require
    [biff.util :as bu]
    [clojure.spec.alpha :as s]
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

(bu/sdefs
  ::display-name (s/and string? #(<= (count %) 20))
  ::user-public (bu/only-keys
                  :opt-un [::display-name])
  :user.public/id uuid?
  ::user-public-ref (bu/only-keys :req [:user.public/id])
  :user/id uuid?
  ::user-ref (bu/only-keys :req [:user/id])
  ::user (bu/only-keys :req [:user/email])
  ::users (s/and set? (s/coll-of :user/id))
  :game/id (s/and string? #(<= 1 (count %) 10))
  ::game-ref (bu/only-keys :req [:game/id])
  :player/x :user/id
  :player/o :user/id
  ::index #{0 1 2}
  ::location (s/and vector? (s/tuple ::index ::index))
  ::player #{:x :o}
  ::board (s/map-of ::location ::player)
  ::game (bu/only-keys
           :req-un [::users]
           :opt-un [:player/x
                    :player/o
                    ::board]))

(defn self? [{:keys [auth-uid] {:keys [user/id]} :doc}]
  (= auth-uid id))

(defn authenticated? [{:keys [auth-uid]}]
  (some? auth-uid))

(defn only-changed-keys? [doc-a doc-b & ks]
  (apply = (map (fn [{:keys [crux.db/id] :as doc}]
                  (apply dissoc (or doc {})
                    (concat ks [:crux.db/id] (when (map? id) (keys id)))))
             [doc-a doc-b])))

(defn only-changed-elements? [doc-a doc-b k & xs]
  (apply = (map #(apply (fnil disj #{}) (get % k) xs) [doc-a doc-b])))

(def rules
  (expand-ops
    {:public-users {:spec [::user-public-ref ::user-public]
                    :get authenticated?
                    :write (fn [{:keys [auth-uid] {:keys [user.public/id]} :doc}]
                             (= auth-uid id))}
     :users {:spec [::user-ref ::user]
             :get (fn [{:keys [auth-uid] {:keys [user/id]} :doc}]
                    (= auth-uid id))}
     :games {:spec [::game-ref ::game]
             :query (fn [{:keys [auth-uid] {:keys [game/id users]} :doc}]
                      (contains? users auth-uid))
             [:create :update] (fn [{:keys [auth-uid doc old-doc]
                                     {:keys [game/id users]} :doc}]
                                 (and
                                   (some #(contains? (:users %) auth-uid) [doc old-doc])
                                   (only-changed-keys? doc old-doc :users)
                                   (only-changed-elements? doc old-doc :users auth-uid)))}}))
