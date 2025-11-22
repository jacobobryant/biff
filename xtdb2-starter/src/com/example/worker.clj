(ns com.example.worker
  (:require [clojure.tools.logging :as log]
            [com.biffweb :as biff :refer [q]]
            [com.biffweb.experimental :as biffx]
            [xtdb.api :as xt]
            [tick.core :as tick]))

(defn every-n-minutes [n]
  (iterate #(tick/>> % (tick/of-minutes n)) (tick/now)))

(defn print-usage [{:keys [biff/conn]}]
  ;; For a real app, you can have this run once per day and send you the output
  ;; in an email.
  (let [[{n-users :cnt}] (biffx/q conn {:select [[[:count '*] :cnt]] :from :user})]
    (log/info "There are" n-users "users.")))

(defn alert-new-user [{:keys [biff/conn]} record]
  (when (and (= (:biff.xtdb/table record) "user")
             (-> (biffx/q conn
                          {:select [[[:count '*] :cnt]]
                           :from :user
                           :where [:= :xt/id (:xt/id record)]}
                          {:snapshot-time (tick/<< (:xt/system-from record)
                                                   (tick/of-nanos 1))})
                 first
                 :cnt
                 (= 0)))
    ;; You could send this as an email instead of printing.
    (log/info "WOAH there's a new user: " (pr-str record))))

(defn echo-consumer [{:keys [biff/job] :as ctx}]
  (prn :echo job)
  (when-some [callback (:biff/callback job)]
    (callback job)))

(def module
  {:tasks [{:task #'print-usage
            :schedule #(every-n-minutes 5)}]
   :on-tx alert-new-user
   :queues [{:id :echo
             :consumer #'echo-consumer}]})
