(ns example.triggers
  (:require
    [crux.api :as crux]
    [trident.util :as u]))

(defn assign-players [{:keys [biff/node doc]
                       {:keys [users x o]} :doc :as env}]
  ; When a user joins or leaves a game, make assignments for X and O as needed.
  ; Delete the game document if everyone has left.
  (let [new-x (when-not (contains? users x)
                (first (shuffle (disj users o))))
        new-o (when-not (contains? users o)
                (first (shuffle (disj users x new-x))))
        {:keys [x o] :as new-doc} (u/assoc-some doc
                                    :x new-x
                                    :o new-o)
        new-doc (cond-> new-doc
                  (not (contains? users x)) (dissoc :x)
                  (not (contains? users o)) (dissoc :o))
        op (cond
             (empty? users) [:crux.tx/delete (:crux.db/id doc)]
             (not= doc new-doc) [:crux.tx/put new-doc])]
    (when op
      (crux/submit-tx node
        [[:crux.tx/match (some :crux.db/id [doc new-doc]) doc]
         op]))))

(def triggers
  {:games {[:create :update] assign-players}})
