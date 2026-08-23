(ns com.biffweb.demo.app.auth
  (:require [com.biffweb.authenticate :as biff.auth]
            [com.biffweb.demo.lib.email :as email]
            [com.biffweb.demo.routes :as routes]
            [com.biffweb.sqlite :as biff.sqlite]
            [next.jdbc :as jdbc])
  (:import [java.time Instant]))

(defn get-user-id [ctx email]
  (:user/id
   (first
    (biff.sqlite/execute ctx {:select [:user/id]
                              :from   :user
                              :where  [:= :user/email email]}))))

(defn- seed-todos [user-id now]
  [{:todo/id         (random-uuid)
    :todo/user-id    user-id
    :todo/title      "Sign in with email"
    :todo/completed  true
    :todo/archived   false
    :todo/created-at (.minusSeconds now 3600)
    :todo/updated-at (.minusSeconds now 1800)}
   {:todo/id         (random-uuid)
    :todo/user-id    user-id
    :todo/title      "Open this app in two tabs"
    :todo/completed  false
    :todo/archived   false
    :todo/created-at (.minusSeconds now 3000)
    :todo/updated-at (.minusSeconds now 3000)}
   {:todo/id         (random-uuid)
    :todo/user-id    user-id
    :todo/title      "Create a todo in one tab and watch the other tab update"
    :todo/completed  false
    :todo/archived   false
    :todo/created-at (.minusSeconds now 2400)
    :todo/updated-at (.minusSeconds now 2400)}
   {:todo/id         (random-uuid)
    :todo/user-id    user-id
    :todo/title      "Use the archive queue button to exercise biff.background"
    :todo/completed  false
    :todo/archived   false
    :todo/created-at (.minusSeconds now 1800)
    :todo/updated-at (.minusSeconds now 1800)}
   {:todo/id          (random-uuid)
    :todo/user-id     user-id
    :todo/title       (str "This archived example appears when you toggle "
                           "archived items on")
    :todo/completed   true
    :todo/archived    true
    :todo/archived-at (.minusSeconds now 600)
    :todo/created-at  (.minusSeconds now 1200)
    :todo/updated-at  (.minusSeconds now 600)}])

(defn create-user! [ctx {:keys [email]}]
  (let [id  (random-uuid)
        now (Instant/now)]
    (jdbc/with-transaction [tx (:biff.sqlite/write-conn ctx)]
      (let [tx-ctx (assoc ctx
                          :biff.sqlite/write-conn tx
                          :biff.sqlite/read-pool tx)]
        (biff.sqlite/execute tx-ctx
                             {:insert-into :user
                              :values      [{:user/id        id
                                             :user/email     email
                                             :user/joined-at now}]})
        (biff.sqlite/execute tx-ctx
                             {:insert-into :todo
                              :values      (seed-todos id now)})))
    id))

(def module
  (biff.auth/module
   (merge
    {:biff.auth/app-path             (routes/app)
     :biff.auth/app-name             "Biff Demo App"
     :biff.auth/primary-color "#0f766e"
     ;; We're using com.biffweb.ring/wrap-csrf-protection.
     :biff.auth/skip-csrf-protection true
     :biff.auth/send-email           #'email/send-email
     :biff.auth/get-user-id          #'get-user-id
     :biff.auth/create-user          #'create-user!}
    biff.auth/turnstile-config)))
