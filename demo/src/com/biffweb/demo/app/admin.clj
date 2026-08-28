(ns com.biffweb.demo.app.admin
  (:require [com.biffweb.admin :as biff.admin]
            [com.biffweb.demo.lib.email :as lib.email]
            [com.biffweb.sqlite :as biff.sqlite]
            [tick.core :as tick]))

(defn- fake-users []
  (mapv (fn [index]
          {:user-id   (keyword "fake-user" (str index))
           :email     (str "user" index "@example.com")
           :joined-at (tick/<< (tick/now)
                               (tick/new-duration (mod index 90) :days))})
        (range 125)))

(defn- get-users [ctx]
  (->> (biff.sqlite/execute ctx {:select   [:user/id
                                            :user/email
                                            :user/joined-at]
                                 :from     :user
                                 :order-by [[:user/joined-at :desc]]})
       (mapv (fn [{:user/keys [id email joined-at]}]
               {:user-id id :email email :joined-at joined-at}))
       (into (fake-users))))

(defn- todo-events [ctx column]
  (->> (biff.sqlite/execute
        ctx
        (cond-> {:select [:todo/user-id column]
                 :from   :todo}
          (= column :todo/updated-at)
          (assoc :where [:<> :todo/updated-at :todo/created-at])))
       (mapv (fn [row]
               {:user-id (:todo/user-id row)
                :instant (get row column)}))))

(defn- signup-events [ctx]
  (->> (biff.sqlite/execute ctx {:select [:user/id :user/joined-at]
                                 :from   :user})
       (mapv (fn [{:user/keys [id joined-at]}]
               {:user-id id
                :instant joined-at}))))

(defn- get-usage-events [ctx]
  (into (vec (concat (signup-events ctx)
                     (todo-events ctx :todo/created-at)
                     (todo-events ctx :todo/updated-at)))
        (map (fn [index]
               {:user-id (keyword "fake-user" (str (mod index 125)))
                :instant (tick/<< (tick/now)
                                  (tick/new-duration (mod index 45) :days))}))
        (range 250)))

(defn- get-revenue-events [_ctx]
  (mapv (fn [index]
          {:instant (tick/<< (tick/now)
                             (tick/new-duration (mod index 45) :days))
           :revenue (+ 5 (mod (* index 7) 95))})
        (range 100)))

(def module
  (biff.admin/module
   {:biff.admin/get-usage-events   #'get-usage-events
    :biff.admin/get-revenue-events #'get-revenue-events
    :biff.admin/get-users          #'get-users
    :biff.admin/send-email         #'lib.email/send-email}))
