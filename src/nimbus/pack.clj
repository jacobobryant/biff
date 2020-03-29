(ns nimbus.pack
  (:require
    [clojure.edn :as edn]
    [trident.util :as u]
    [taoensso.timbre :as timbre :refer [trace debug info warn error tracef debugf infof warnf errorf]]
    [nimbus.core :as nimbus :refer [api]]))

(def subscriptions (atom #{}))

(defmethod api ::subscribe
  [{:keys [send-fn uid] :as event} _]
  (swap! subscriptions conj uid)
  (send-fn uid [::subscribe
                {:query nil
                 :changeset {[::deps nil]
                             (edn/read-string (slurp "deps.edn"))}}])
  nil)

