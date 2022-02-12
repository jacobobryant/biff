(ns com.example.feat.worker
  (:require [com.biffweb :as biff :refer [q]]
            [xtdb.api :as xt]))

(defn every-minute []
  (iterate #(biff/add-seconds % 60) (java.util.Date.)))

(defn print-usage [{:keys [biff/db]}]
  ;; For a real app, you can have this run once per day and send you the output
  ;; in an email.
  (let [n-users (nth (q db
                        '{:find (count user)
                          :where [[user :user/email]]})
                     0
                     0)]
    (println "There are" n-users "users."
             "(This message gets printed every 60 seconds. You can disable it"
             "by setting `:example/worker false` in config.edn)")))

(defn alert-new-user [{:keys [example/worker biff.xtdb/node]} tx]
  ;; This also could be an email alert.
  (doseq [_ [nil]
          :when worker
          :let [db-before (xt/db node {::xt/tx-id (dec (::xt/tx-id tx))})]
          [op & args] (::xt/tx-ops tx)
          :when (= op ::xt/put)
          :let [[doc] args]
          :when (and (contains? doc :user/email)
                     (nil? (xt/entity db-before (:xt/id doc))))]
    (println "WOAH there's a new user")))

(def features
  {:tasks [{:task #'print-usage
            :schedule every-minute
            :enabled (fn [sys]
                       (:example/worker sys))}]
   :on-tx alert-new-user})
