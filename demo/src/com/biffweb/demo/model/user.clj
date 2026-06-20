(ns com.biffweb.demo.model.user
  (:require [com.biffweb.graph :as biff.graph]))

(def session-user-fields
  [:user/id])

(biff.graph/defresolver session-user
  {:output [{:session/user session-user-fields}]}
  [{:keys [session]} _]
  (when-let [uid (:uid session)]
    {:session/user {:user/id uid}}))

(def module
  {:biff.graph/resolvers [session-user]})
