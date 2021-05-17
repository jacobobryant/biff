(ns {{parent-ns}}.rules
  (:require [biff.crux :refer [authorize]]
            [biff.misc :as misc]))

; See https://biff.findka.com/#authorization-rules

(def registry
  {:user/id     :uuid
   :user/email  :string
   :user/foo    :string
   :user/bar    :string
   :user        [:map {:closed true}
                 [:crux.db/id :user/id]
                 :user/email
                 [:user/foo {:optional true}]
                 [:user/bar {:optional true}]]
   :msg/id      :uuid
   :msg/user    :user/id
   :msg/text    :string
   :msg/sent-at inst?
   :msg         [:map {:closed true}
                 [:crux.db/id :msg/id]
                 :msg/user
                 :msg/text
                 :msg/sent-at]})

(def schema (misc/map->MalliSchema
              {:doc-types [:user :msg]
               :malli-opts {:registry (misc/malli-registry registry)}}))

(defmethod authorize [:msg :create]
  [{:keys [biff/uid]} {:keys [msg/user]}]
  (= uid user))

(defmethod authorize [:msg :delete]
  [{:keys [biff/uid]} {:keys [msg/user]}]
  (= uid user))

(defmethod authorize [:msg :query]
  [_ _]
  true)

(defmethod authorize [:user :get]
  [{:keys [biff/uid]} {:keys [crux.db/id]}]
  (= uid id))

(defmethod authorize [:user :update]
  [{:keys [biff/uid]} before {:keys [crux.db/id] :as after}]
  (and (= uid id)
       (apply = (map #(dissoc % :user/foo) [before after]))))
