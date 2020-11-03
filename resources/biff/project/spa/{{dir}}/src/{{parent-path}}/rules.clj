(ns {{parent-ns}}.rules
  (:require
    [biff.rules :as brules]
    [biff.util :as bu]
    [clojure.spec.alpha :as s]))

; See https://findka.com/biff/#rules

; Same as (do (s/def ::text string?) ...)
(bu/sdefs
  ::text string?
  ::timestamp inst?
  :user/id uuid?
  ::message (bu/only-keys
              :req [:user/id]
              :req-un [::text
                       ::timestamp])
  ::user-ref (bu/only-keys :req [:user/id])
  ::foo string?
  ::bar string?
  ::user (s/keys
           :req [:user/email]
           :opt-un [::foo ::bar]))

(def rules
  {:messages {:spec [uuid? ::message]
              :query (constantly true)
              :create (fn [{:keys [session/uid] {:keys [user/id]} :doc}]
                        (= uid id))
              :delete (fn [{:keys [session/uid] {:keys [user/id]} :doc-before}]
                        (= uid id))}
   :users {:spec [::user-ref ::user]
           :get (fn [{:keys [session/uid] {:keys [user/id]} :doc}]
                  (= uid id))
           :update (fn [{:keys [session/uid doc doc-before] {:keys [user/id]} :doc}]
                     (and
                       (= uid id)
                       (brules/only-changed-keys? doc doc-before :foo)))}})
