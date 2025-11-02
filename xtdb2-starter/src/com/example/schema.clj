(ns com.example.schema)

(def ? {:optional true})

(def schema
  {::string [:string {:max 1000}]

   :user [:map {:closed true}
          [:xt/id       :uuid]
          [:email       ::string]
          [:joined-at   inst?]
          [:foo       ? ::string]
          [:bar       ? ::string]]

   :msg [:map {:closed true}
         [:xt/id   :uuid]
         [:user    :uuid]
         [:content [:string {:max 10000}]]
         [:sent-at inst?]]})

(def module
  {:schema schema})
