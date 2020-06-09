(ns example.client.app.mutations
  (:require
    [clojure.pprint :refer [pprint]]
    [example.client.app.db :as db]
    [example.client.app.system :as s]))

(defmulti api (comp first :?data))
(defmethod api :default
  [{[event-id] :?data} data]
  (println "unhandled event:" event-id))

(defmethod api :biff/error
  [_ anom]
  (pprint anom))

(defmethod api :example/prn
  [_ arg]
  (prn arg))

(defn api-send [& args]
  (apply (:api-send @s/system) args))

(defn set-display-name [display-name]
  (api-send
    [:biff/tx
     {[:public-users {:user.public/id @db/uid}]
      {:db/merge true
       :display-name (or (not-empty display-name) :db/remove)}}]))

(defn set-game-id [game-id]
  (when (not= game-id @db/game-id)
    (api-send
      [:biff/tx
       (cond-> {}
         (not-empty @db/game-id)
         (assoc [:games {:game/id @db/game-id}]
           {:db/update true
            :users [:db/disj @db/uid]})

         (not-empty game-id)
         (assoc [:games {:game/id game-id}]
           {:db/merge true
            :users [:db/union @db/uid]}))])))

(defn move [location]
  (api-send [:example/move {:game-id @db/game-id :location location}]))

(defn new-game []
  (api-send [:example/new-game {:game-id @db/game-id}]))
