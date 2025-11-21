(ns com.example.schema)

(def ? {:optional true})

(def schema
  {::string [:string {:max 1000}]

   :user [:map {:closed true}
          [:xt/id            :uuid]
          [:user/email       ::string]
          [:user/joined-at   inst?]
          [:user/foo       ? ::string]
          [:user/bar       ? ::string]]

   :msg [:map {:closed true}
         [:xt/id       :uuid]
         [:msg/user    :uuid]
         [:msg/content [:string {:max 10000}]]
         [:msg/sent-at inst?]]})

(def module
  {:schema schema})
