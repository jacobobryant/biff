(ns com.biffweb.demo.model.user)

(def session-user-fields
  [:user/id])

(defn session-user
  {:output [{:session/user session-user-fields}]}
  [{:keys [session]} _]
  (when-let [uid (:uid session)]
    {:session/user {:user/id uid}}))

(def module
  {:biff.graph/resolvers [#'session-user]})
