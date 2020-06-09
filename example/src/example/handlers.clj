(ns example.handlers
  (:require
    [trident.util :as u]
    [clojure.spec.alpha :as s]
    [crux.api :as crux]
    [example.logic :as logic]))

(defmulti api :id)

(defmethod api :default
  [{:keys [id]} _]
  (u/anom :not-found (str "No method for " id)))

(defmethod api :example/move
  [{:keys [biff/node biff/db session/uid] :as sys} {:keys [game-id location]}]
  (let [{:keys [board users x o] :as game} (crux/entity db {:game/id game-id})
        current-player (logic/current-player game)
        new-game (assoc-in game [:board location] current-player)
        allowed (and
                  (not (logic/game-over? game))
                  (= uid (get game current-player :none))
                  (not (contains? board location))
                  (s/valid? :example.rules/location location))]
    (when allowed
      (crux/submit-tx node
        [[:crux.tx/match {:game/id game-id} game]
         [:crux.tx/put new-game]])
      nil)))

(defmethod api :example/new-game
  [{:keys [biff/node biff/db session/uid] :as sys} {:keys [game-id]}]
  (let [{:keys [board users x o] :as game} (crux/entity db {:game/id game-id})
        new-game (-> game
                   (dissoc :board)
                   (assoc :x o :o x))
        allowed (and
                  (logic/game-over? game)
                  (#{x o} uid))]
    (when allowed
      (crux/submit-tx node
        [[:crux.tx/match {:game/id game-id} game]
         [:crux.tx/put new-game]])
      nil)))

(defmethod api :example/echo
  [{:keys [client-id biff/send-event]} arg]
  (send-event client-id [:example/prn ":example/echo called"])
  arg)
