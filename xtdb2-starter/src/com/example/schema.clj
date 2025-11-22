(ns com.example.schema
  (:require [tick.core :as tick]))

(def ? {:optional true})

(def schema
  {::string [:string {:max 1000}]
   ::zdt    [:fn tick/zoned-date-time?]

   :user [:map {:closed true}
          [:xt/id            :uuid]
          [:user/email       ::string]
          [:user/joined-at   ::zdt]
          [:user/foo       ? ::string]
          [:user/bar       ? ::string]]

   :msg [:map {:closed true}
         [:xt/id       :uuid]
         [:msg/user    :uuid]
         [:msg/content [:string {:max 10000}]]
         [:msg/sent-at ::zdt]]})

(def module
  {:schema schema})
