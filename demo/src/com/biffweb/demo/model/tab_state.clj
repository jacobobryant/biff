(ns com.biffweb.demo.model.tab-state
  (:require [com.biffweb.graph :as biff.graph]
            [com.biffweb.sqlite :as biff.sqlite]))

(def ui-state-fields
  [:todo/filter :todo/show-archived])

(def default-ui-state
  {:todo/filter        :todo.filter/all
   :todo/show-archived false})

(defn tab-state-key [{:keys [session biff.datastar/tab-id]}]
  (when (and (:uid session) tab-id)
    (str (:uid session) ":" tab-id)))

(biff.graph/defresolver tab-state-id
  {:output [:tab-state/id]}
  [ctx _]
  (when-some [id (tab-state-key ctx)]
    {:tab-state/id id}))

(biff.graph/defresolver tab-state
  {:output [{:session/tab-state ui-state-fields}]}
  [ctx _]
  (when-some [id (tab-state-key ctx)]
    (let [data (some-> (biff.sqlite/execute
                        ctx
                        {:select [:tab-state/data]
                         :from   :tab-state
                         :where  [:= :tab-state/id id]})
                       first
                       :tab-state/data)]
      {:session/tab-state (merge default-ui-state (or data {}))})))

(def module
  {:biff.graph/resolvers [tab-state-id tab-state]})
