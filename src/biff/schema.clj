(ns biff.schema
  (:require
    [biff.util :as bu]
    [clojure.spec.alpha :as s]))

(bu/sdefs
  ::jwt-key string?
  ::cookie-key string?
  :biff/auth-keys (bu/only-keys :opt-un [::jwt-key ::cookie-key]))

(def rules
  {:biff/auth-keys {:spec [#{:biff.auth/keys} :biff/auth-keys]}})
