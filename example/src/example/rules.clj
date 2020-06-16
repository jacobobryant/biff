(ns example.rules
  (:require
    [biff.rules :as brules]
    [trident.util :as u]
    [clojure.spec.alpha :as s]))

; Same as (do (s/def ...) ...)
(u/sdefs
  ::display-name (s/and string? #(<= (count %) 20))
  ::user-public (u/only-keys
                  :opt-un [::display-name])
  :user.public/id uuid?
  ::user-public-ref (u/only-keys :req [:user.public/id])
  :user/id uuid?
  ::user-ref (u/only-keys :req [:user/id])
  ::user (s/keys :req [:user/email])
  ::users (s/and set? (s/coll-of :user/id))
  :game/id (s/and string? #(<= 1 (count %) 10))
  ::game-ref (u/only-keys :req [:game/id])
  :player/x :user/id
  :player/o :user/id
  ::index #{0 1 2}
  ::location (s/and vector? (s/tuple ::index ::index))
  ::player #{:x :o}
  ::board (s/map-of ::location ::player)
  ::game (u/only-keys
           :req-un [::users]
           :opt-un [:player/x
                    :player/o
                    ::board]))

(def rules
  {:public-users {:spec [::user-public-ref ::user-public]
                  :get brules/authenticated?
                  :write (fn [{:keys [session/uid] {:keys [user.public/id]} :doc}]
                           (= uid id))}
   :users {:spec [::user-ref ::user]
           :get (fn [{:keys [session/uid] {:keys [user/id]} :doc}]
                  (= uid id))}
   :games {:spec [::game-ref ::game]
           :query (fn [{:keys [session/uid] {:keys [users]} :doc}]
                    (contains? users uid))
           [:create :update] (fn [{:keys [session/uid doc old-doc]
                                   {:keys [users]} :doc}]
                               (and
                                 (some #(contains? (:users %) uid) [doc old-doc])
                                 (brules/only-changed-keys? doc old-doc :users)
                                 (brules/only-changed-elements? doc old-doc :users uid)))}})
