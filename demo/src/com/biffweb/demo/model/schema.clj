(ns com.biffweb.demo.model.schema
  (:require [clojure.string :as str]
            [com.biffweb.sqlite :as biff.sqlite]))

(def columns
  {:tab-state/data {:type :edn}
   :tab-state/id   {:type :text :primary-key true}

   :todo/archived    {:type :boolean :required true :index true}
   :todo/archived-at {:type :inst}
   :todo/completed   {:type :boolean :required true}
   :todo/created-at  {:type :inst :required true :index true}
   :todo/id          {:type :uuid :primary-key true}
   :todo/title       {:type :text :required true}
   :todo/updated-at  {:type :inst :required true :index true}
   :todo/user-id     {:type :uuid :required true :ref :user/id :index true}

   :user/email     {:type :text :required true :unique true}
   :user/id        {:type :uuid :primary-key true}
   :user/joined-at {:type :inst :required true :index true}})

(defn only-fields-edited? [before after fields]
  (= (apply dissoc before fields)
     (apply dissoc after fields)))

;; Since this is currently empty, user records can't actually be updated. This
;; could be used for e.g. user settings.
(def editable-user-fields [])

(defn authorize-entry [{{:keys [uid]} :session}
                       {:keys [table op before after]}]
  (case table
    :user
    (and (every? #{uid} (keep :user/id [before after]))
         (case op
           :create false
           :update (only-fields-edited? before after editable-user-fields)
           :delete true))

    :tab-state
    (every? #(str/starts-with? % (str uid ":"))
            (keep :tab/id [before after]))

    :todo
    (every? #{uid} (keep :todo/user-id [before after]))

    false))

(defn authorize
  [ctx diff]
  (every? #(authorize-entry ctx %) diff))

(def extra-init-sql [])

(defn init [_modules-var]
  {:biff.sqlite/extra-init-sql extra-init-sql
   :biff.sqlite/authorize      #'authorize})

(def module
  {:biff.core/init       init
   :biff.sqlite/columns  columns
   :biff.graph/resolvers (biff.sqlite/make-resolvers
                          {:biff.sqlite/columns columns})})
