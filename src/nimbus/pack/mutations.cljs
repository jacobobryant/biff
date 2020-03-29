(ns nimbus.pack.mutations
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
    [cljs.core.async :as async :refer [<! >! put! chan]]
    [trident.util :as u :refer [capture-env]]))

(defmulti api :id)
(defmethod api :default
  [{:keys [id]} _]
  (println "unhandled event:" id))

(def env (capture-env 'nimbus.pack.mutations))
