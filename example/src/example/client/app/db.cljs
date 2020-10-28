(ns example.client.app.db
  (:require
    [example.logic :as logic]
    [trident.util :as u]
    [rum.core]))

(defonce db (atom {}))
(defonce sub-data (atom {}))

; same as (do
;           (rum.core/derived-atom [sub-data] :example.client.app.db/data
;             (fn [sub-data]
;               (apply merge-with merge (vals sub-data))))
;           ...)
(u/defderivations [sub-data] example.client.app.db
  data (apply merge-with merge (vals sub-data))

  uid (get-in data [:uid nil :uid])
  user-ref {:user/id uid}
  id->users (:users data)
  self (get id->users user-ref)
  email (:user/email self)
  signed-in (and (some? uid) (not= :signed-out uid))

  id->public-users (:public-users data)
  public-self (get id->public-users {:user.public/id uid})
  display-name (:display-name public-self)

  game (->> data
         :games
         vals
         (filter #(contains? (:users %) uid))
         first)
  game-id (:game/id game)

  participants (:users game)
  x (:x game)
  o (:o game)
  board (:board game)

  current-player (get game (logic/current-player game))
  winner (get game (logic/winner game))
  game-over (logic/game-over? game)
  draw (and game-over (not winner))

  biff-subs [; :uid is a special non-Crux query. Biff will respond
             ; with the currently authenticated user's ID.
             :uid
             (when signed-in
               [{:table :users
                 :id user-ref}
                {:table :public-users
                 :id {:user.public/id uid}}
                {:table :games
                 :where [[:users uid]]}])
             (for [u (:users game)]
               {:table :public-users
                :id {:user.public/id u}})]
  subscriptions (->> biff-subs
                  flatten
                  (filter some?)
                  (map #(vector :biff/sub %))
                  set))
