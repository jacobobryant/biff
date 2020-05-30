(ns hello.client.app.components
  (:require
    [clojure.string :as str]
    [clojure.pprint :refer [pprint]]
    [hello.client.app.db :as db]
    [hello.client.app.mutations :as m]
    [hello.logic :as logic]
    [rum.core :as rum :refer [defc defcs react reactive static fragment local]]))

(defn gap
  ([] (gap "1rem"))
  ([size] (gap size size))
  ([width height] [:div {:style {:width width :height height}}]))

(defcs game-id < reactive (local nil ::tmp-value)
  [{::keys [tmp-value]}]
  [:div
   [:div "Game ID:"]
   (gap "0.3rem")
   [:input {:type "text"
            :value (or @tmp-value (react db/game-id) "")
            :on-change #(reset! tmp-value (.. % -target -value))
            :on-blur #(do
                        (m/set-game-id (.. % -target -value))
                        (reset! tmp-value nil))}]])

(defcs display-name < reactive (local nil ::tmp-value)
  [{::keys [tmp-value]}]
  [:div
   [:div "Display name:"]
   (gap "0.3rem")
   [:input {:type "text"
            :value (or @tmp-value (react db/display-name) "")
            :on-change #(reset! tmp-value (.. % -target -value))
            :on-blur #(do
                        (m/set-display-name (.. % -target -value))
                        (reset! tmp-value nil))}]])

(defc participants < reactive
  []
  (when (some? (react db/game))
    [:div
     [:div "Participants:"]
     [:ul
      (for [u (react db/participants)]
        [:li {:key u}
         (logic/id->name (react db/id->public-users) u)
         (when (= u (react db/x))
           [:span " (" [:strong "X"] ")"])
         (when (= u (react db/o))
           [:span " (" [:strong "O"] ")"])])]]))

(defc board < reactive
  []
  (when (some? (react db/game))
    [:div
     (for [row (range 3)]
       [:div {:key row
              :style {:display "flex"
                      :align-items "center"}}
        (for [col (range 3)]
          [:button {:key col
                    :on-click #(m/move [row col])
                    :style {:width "4rem"
                            :height "4rem"
                            :font-size "2rem"}}
           [:strong (some-> (react db/board)
                      (get [row col])
                      name
                      str/upper-case)]])])]))

(defc info < reactive
  []
  (when (some? (react db/game))
    [:div
     (if (react db/game-over)
       [:div
        [:p (str "Game over. "
              (if-some [winner (react db/winner)]
                (str (logic/id->name (react db/id->public-users) winner)
                  " won.")
                "It's a draw."))]
        [:button {:on-click m/new-game} "New game"]]
       [:p "It's "
        (logic/id->name (react db/id->public-users)
          (react db/current-player))
        "'s turn." ])]))

(defc main < reactive
  []
  [:div
   (when-some [email (react db/email)]
     [:p "Signed in as " email " ("
      [:a {:href "/api/signout"} "sign out"] ")"])
   (display-name)
   (gap "1rem")
   (game-id)
   (gap "1rem")
   (participants)
   (gap "1rem")
   (board)
   (gap "1rem")
   (info)
   [:pre (with-out-str (pprint (react db/data)))]])
