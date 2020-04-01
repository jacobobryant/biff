(ns nimbus.pack.mutations
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
    [nimbus.pack.db :as db]
    [cljs.core.async :as async :refer [<! >! put! chan]]))

(defmulti api :id)
(defmethod api :default
  [{:keys [id]} _]
  (println "unhandled event:" id))

(defn api-send [& args]
  (apply (:api-send @db/env) args))
