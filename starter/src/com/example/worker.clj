(ns com.example.worker
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]
            [com.biffweb :as biff :refer [q]]
            [xtdb.api :as xt]))

(defn every-n-minutes [n]
  (iterate #(biff/add-seconds % (* 60 n)) (java.util.Date.)))

(defn print-usage [ctx]
  ;; For a real app, you can have this run once per day and send you the output
  ;; in an email.
  (let [{:keys [n-users]} (biff/index-snapshots ctx)]
    (log/info "There are" (get (xt/entity n-users :n-users) :value 0) "users.")))

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
      (when (some #(some-> (:msg/text %) (str/includes? "ERROR")) docs)
        (throw (ex-info "Got a message with 'ERROR' in it!" {:docs docs})))
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
              :indexer #'index-interesting-words
              ;; If you uncomment this, the indexer will stop running if it throws an exception. Good for developing a
              ;; new index before you start using it in application code. After that, probably best to let the indexer
              ;; keep running on future transactions even if some of them fail.
              ;:abort-on-error true
              }]})
