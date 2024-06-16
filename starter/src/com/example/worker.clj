(ns com.example.worker
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]
            [com.biffweb :as biff :refer [q]]
            [xtdb.api :as xt]))

(defn every-n-minutes [n]
  (iterate #(biff/add-seconds % (* 60 n)) (java.util.Date.)))

(defn print-usage [{:keys [biff/db]}]
  ;; For a real app, you can have this run once per day and send you the output
  ;; in an email.
  (let [n-users (nth (q db
                        '{:find (count user)
                          :where [[user :user/email]]})
                     0
                     0)]
    (log/info "There are" n-users "users.")))

(defn alert-new-user [{:keys [biff.xtdb/node]} tx]
  (doseq [_ [nil]
          :let [db-before (xt/db node {::xt/tx-id (dec (::xt/tx-id tx))})]
          [op & args] (::xt/tx-ops tx)
          :when (= op ::xt/put)
          :let [[doc] args]
          :when (and (contains? doc :user/email)
                     (nil? (xt/entity db-before (:xt/id doc))))]
    ;; You could send this as an email instead of printing.
    (log/info "WOAH there's a new user")))

(defn echo-consumer [{:keys [biff/job] :as ctx}]
  (prn :echo job)
  (when-some [callback (:biff/callback job)]
    (callback job)))

(defn index-n-users [{:biff.index/keys [docs db]}]
  (let [tx (into []
                 (keep (fn [{:keys [user/email]}]
                         (when (and (some? email)
                                    (empty? (xt/entity db email)))
                           [::xt/put {:xt/id email}])))
                 docs)]
    (when (not-empty tx)
      (conj tx [::xt/put {:xt/id :n-users
                          :value (+ (:value (xt/entity db :n-users) 0) (count tx))}]))))

(let [patterns [#"(?i)hello"
                #"(?i)there"]]
  (defn index-interesting-words [{:biff.index/keys [docs db]}]
    (let [n-words (count (for [{:keys [msg/text]} docs
                               :when text
                               pattern patterns
                               match (re-seq pattern text)]
                           match))]
      (when (< 0 n-words)
        [[::xt/put {:xt/id :n-words
                    :value (+ (:value (xt/entity db :n-words) 0) n-words)}]]))))

(def module
  {:tasks [{:task #'print-usage
            :schedule #(every-n-minutes 5)}]
   :on-tx alert-new-user
   :queues [{:id :echo
             :consumer #'echo-consumer}]
   :indexes [{:id :n-users
              :version 1
              :indexer #'index-n-users}
             {:id :n-interesting-words
              :version 1
              :indexer #'index-interesting-words}]})
