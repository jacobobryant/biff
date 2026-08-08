(ns com.biffweb.demo.model.todo
  (:require [com.biffweb.demo.model.tab-state :as model.tab-state]
            [com.biffweb.graph :as biff.graph]
            [com.biffweb.sqlite :as biff.sqlite]))

(def ui-state-fields
  [:todo/filter :todo/show-archived])

(def todo-fields
  [:todo/id
   :todo/title
   :todo/completed
   :todo/archived
   [:? :todo/archived-at]
   :todo/created-at
   :todo/updated-at])

(def default-ui-state
  {:todo/filter        :todo.filter/all
   :todo/show-archived false})

(biff.graph/defresolver todo-ui-state
  {:output [{:todo/ui-state ui-state-fields}]}
  [ctx _]
  (let [tab-state (when-some [tab-state-id (model.tab-state/tab-state-key ctx)]
                    (some-> (biff.sqlite/execute
                             ctx
                             {:select [:tab-state/data]
                              :from   :tab-state
                              :where  [:= :tab-state/id tab-state-id]})
                            first
                            :tab-state/data))]
    {:todo/ui-state (merge default-ui-state (or tab-state {}))}))

(biff.graph/defresolver admin-link-visible?
  {:input  [{[:? :session/user] [:user/id]}]
   :output [:app/show-admin-link?]}
  [{:keys [biff.admin/user-id]} {:keys [session/user]}]
  (let [configured-admin-id (some-> user-id str not-empty)
        current-user-id     (some-> user :user/id str)]
    {:app/show-admin-link? (or (nil? configured-admin-id)
                               (= configured-admin-id current-user-id))}))

(defn- apply-filter [filter-k todos]
  (case filter-k
    :todo.filter/active (filterv (complement :todo/completed) todos)
    :todo.filter/completed (filterv :todo/completed todos)
    (vec todos)))

(biff.graph/defresolver user-todos
  {:input  [{:session/user [:user/id]}
            {[:? :todo/ui-state] ui-state-fields}]
   :output [{:todo/items todo-fields}
            {:todo/archived-items todo-fields}
            :todo/active-count
            :todo/completed-count
            :todo/archived-count
            :todo/remaining-count]}
  :start
  (fn [_ctx {:keys [session/user todo/ui-state]}]
    {:todo-rows    [:biff.sqlite.fx/execute
                    {:select   :*
                     :from     :todo
                     :where    [:= :todo/user-id (:user/id user)]
                     :order-by [[:todo/created-at :asc]
                                [:todo/id :asc]]}]
     :ui-state     (merge default-ui-state ui-state)
     :biff.fx/next :finish})

  :finish
  (fn [{:keys [todo-rows ui-state]} _]
    (let [active-items   (filterv (complement :todo/archived) todo-rows)
          archived-items (filterv :todo/archived todo-rows)
          visible-items  (apply-filter (:todo/filter ui-state) active-items)]
      {:todo/items           visible-items
       :todo/archived-items  (if (:todo/show-archived ui-state)
                               archived-items
                               [])
       :todo/active-count    (count (filter (complement :todo/completed)
                                            active-items))
       :todo/completed-count (count (filter :todo/completed active-items))
       :todo/archived-count  (count archived-items)
       :todo/remaining-count (count (filter (complement :todo/completed)
                                            active-items))})))

(def module
  {:biff.graph/resolvers [todo-ui-state
                          admin-link-visible?
                          user-todos]})
