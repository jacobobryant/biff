(ns demo.store)

(defn atom-store []
  (let [store (atom {:users {} :kv {}})]
    {::store store

     :biff.auth/get-user-id
     (fn [_ctx email]
       (get-in @store [:users email :user/id]))

     :biff.auth/create-user
     (fn [_ctx {:keys [email params]}]
       (let [user-id (random-uuid)]
         (swap! store assoc-in [:users email]
                {:user/id        user-id
                 :user/email     email
                 :user/params    params
                 :user/joined-at (java.time.Instant/now)})
         user-id))

     :biff.core/kv-get
     (fn [_ctx namespace key]
       (get-in @store [:kv namespace key]))

     :biff.core/kv-set
     (fn [_ctx namespace key value]
       (swap! store assoc-in [:kv namespace key] value)
       nil)}))
