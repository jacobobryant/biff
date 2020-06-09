(ns example.logic)

(defn check-line [board origin dir]
  (let [locations (doall (take 3 (iterate #(mapv + % dir) origin)))
        player (get board origin)]
    (and
      (every? #(= player (get board %)) locations)
      player)))

(defn winner [{:keys [board]}]
  (some #(check-line board (first %) (second %))
    [[[0 0] [0 1]]
     [[1 0] [0 1]]
     [[2 0] [0 1]]
     [[0 0] [1 0]]
     [[0 1] [1 0]]
     [[0 2] [1 0]]
     [[0 0] [1 1]]
     [[2 0] [-1 1]]]))

(defn game-over? [{:keys [board] :as game}]
  (or (winner game) (<= 9 (count board))))

(defn current-player [{:keys [board] :as game}]
  (get [:x :o] (mod (count (keys board)) 2)))

(defn id->name [id->public-users user-id]
  (get-in id->public-users
    [{:user.public/id user-id} :display-name] "Anonymous"))
