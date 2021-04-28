(ns {{parent-ns}}.rules
  (:require
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
  ::user (s/keys
           :req [:user/email]
           :opt-un [::foo]))

(def rules
  {:messages {:spec [uuid? ::message]}
   :users {:spec [::user-ref ::user]}})
