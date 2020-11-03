(ns {{parent-ns}}.triggers
  (:require
    [clojure.pprint :as pp]))

; See https://findka.com/biff/#triggers

(defn do-something [{:keys [doc-before doc op] :as sys}]
  (println "Trigger running:")
  (pp/pprint (select-keys sys [:doc :doc-before :op :table]))
  (println))

(def triggers
  {:users {:write #(do-something (assoc % :table :users))}
   :messages {[:create :delete] #(do-something (assoc % :table :messages))}})
