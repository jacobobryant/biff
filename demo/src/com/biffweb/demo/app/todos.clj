(ns com.biffweb.demo.app.todos
  (:require [clojure.string :as str]
            [com.biffweb.datastar :as biff.datastar]
            [com.biffweb.demo.lib.middleware :as mid]
            [com.biffweb.demo.lib.ui :as ui]
            [com.biffweb.demo.model.tab-state :as model.tab-state]
            [com.biffweb.demo.model.todo :as model.todo]
            [com.biffweb.demo.routes :as routes]
            [com.biffweb.ring :refer [defroute]]
            [com.biffweb.sqlite :as biff.sqlite]))

(defn- post [path]
  (str "@post(" (pr-str path) ")"))

(defn- trim-to-nil [s]
  (let [s (some-> s str str/trim)]
    (not-empty s)))

(defn- camel-case [s]
  (let [[part & parts] (str/split s #"-")]
    (apply str part (map str/capitalize parts))))

(defn- param-value [ctx k]
  (let [kebab (name k)
        camel (camel-case kebab)]
    (some-> (some #(apply find %)
                  [[(:params ctx) kebab]
                   [(:params ctx) camel]
                   [(:params ctx) k]
                   [(:form-params ctx) kebab]
                   [(:form-params ctx) camel]
                   [(:form-params ctx) k]
                   [(:body-params ctx) kebab]
                   [(:body-params ctx) camel]
                   [(:body-params ctx) k]
                   [(:biff.datastar/signals ctx) (keyword kebab)]
                   [(:biff.datastar/signals ctx) (keyword camel)]
                   [(:biff.datastar/signals ctx) k]])
            val)))

(defn- boolean-param [ctx k]
  (contains? #{"1" "true" "on" "yes"} (str/lower-case (str (param-value ctx k)))))

(defn- todo-filter [value]
  (case value
    "active" :todo.filter/active
    "completed" :todo.filter/completed
    :todo.filter/all))

(defn- filter-label [filter-k]
  (case filter-k
    :todo.filter/active "Active"
    :todo.filter/completed "Completed"
    "All"))

(defn- format-instant [instant]
  (str instant))

(defn- input-signal [name value]
  {(keyword (str "data-signals:" name "__ifmissing"))
   (if (empty? value) "''" (pr-str value))})

(defn- current-filter-value [filter-k]
  (case filter-k
    :todo.filter/active "active"
    :todo.filter/completed "completed"
    "all"))

(defn- set-signal [name value]
  (str "$" name " = " (if (string? value) (pr-str value) value) "; "))

(defn- set-signal-expr [name expr]
  (str "$" name " = " expr "; "))

(defn- upsert-tab-state! [ctx tab-state patch]
  (when-some [tab-state-id (model.tab-state/tab-state-key ctx)]
    (let [data (merge model.todo/default-ui-state
                      (or tab-state {})
                      patch)]
      (biff.sqlite/authorized-write
       ctx
       {:insert-into   :tab-state
        :values        [{:tab-state/id   tab-state-id
                         :tab-state/data [:lift data]}]
        :on-conflict   [:tab-state/id]
        :do-update-set [:tab-state/data]}))))

(defn- current-todo [ctx]
  (first
   (biff.sqlite/execute
    ctx
    {:select [:todo/completed :todo/archived]
     :from   :todo
     :where  [:= :todo/id (get-in ctx [:path-params :id])]})))

(defn- toggle-todo! [{:keys [biff.fx/now] :as ctx}]
  (let [todo       (current-todo ctx)
        completed? (if (some? (param-value ctx :completed))
                     (boolean-param ctx :completed)
                     (not (:todo/completed todo)))]
    (biff.sqlite/authorized-write
     ctx
     {:update :todo
      :set    {:todo/completed  completed?
               :todo/updated-at now}
      :where  [:= :todo/id (get-in ctx [:path-params :id])]})))

(defn- archive-todo! [{:keys [biff.fx/now] :as ctx}]
  (let [todo      (current-todo ctx)
        archived? (if (some? (param-value ctx :archived))
                    (boolean-param ctx :archived)
                    (not (:todo/archived todo)))]
    (biff.sqlite/authorized-write
     ctx
     {:update :todo
      :set    {:todo/archived    archived?
               :todo/archived-at (when archived? now)
               :todo/updated-at  now}
      :where  [:= :todo/id (get-in ctx [:path-params :id])]})))

(defn- update-ui-state! [ctx tab-state]
  (upsert-tab-state!
   ctx
   tab-state
   (cond-> {}
     (some? (param-value ctx :filter))
     (assoc :todo/filter (todo-filter (param-value ctx :filter)))

     (some? (param-value ctx :show-archived))
     (assoc :todo/show-archived (boolean-param ctx :show-archived)))))

(defn- counter-pill [label value]
  [:div.rounded-full.bg-slate-100.px-3.py-1.text-sm.font-medium.text-slate-700
   [:span.text-slate-500 label]
   [:span.ml-2.text-slate-950 value]])

(defn- todo-item [todo]
  [:article.rounded-2xl.border.border-slate-200.bg-white.p-4.shadow-sm
   [:div.flex.items-start.justify-between.gap-4
    [:div.flex.gap-3
     [:input.mt-1.h-5.w-5.rounded.border-slate-300.text-teal-600
      {:type           "checkbox"
       :checked        (:todo/completed todo)
       :data-on:change (str (set-signal-expr "completed" "el.checked")
                            (post (routes/todo-toggle (:todo/id todo))))}]
     [:div.space-y-2
      [:p.text-base.font-medium.text-slate-950
       {:class (when (:todo/completed todo) "line-through text-slate-400")}
       (:todo/title todo)]
      [:div.flex.flex-wrap.gap-2.text-xs.text-slate-500
       [:span (str "Created " (format-instant (:todo/created-at todo)))]
       [:span (str "Updated " (format-instant (:todo/updated-at todo)))]
       (when (:todo/completed todo)
         [:span.rounded-full.bg-emerald-100.px-2.py-1.font-medium.text-emerald-700
          "Completed"])]]]
    [:button.rounded-lg.border.border-slate-300.px-3.py-2.text-sm.font-medium.text-slate-700.hover:border-slate-400.hover:text-slate-950
     {:type          "button"
      :data-on:click (str (set-signal "archived" true)
                          (post (routes/todo-archive (:todo/id todo))))}
     "Archive"]]])

(defn- archived-item [todo]
  [:article.rounded-2xl.border.border-dashed.border-slate-300.bg-slate-50.p-4
   [:div.flex.items-start.justify-between.gap-4
    [:div.space-y-2
     [:p.text-base.font-medium.text-slate-600 (:todo/title todo)]
     [:div.flex.flex-wrap.gap-2.text-xs.text-slate-500
      [:span (str "Updated " (format-instant (:todo/updated-at todo)))]
      (when-let [archived-at (:todo/archived-at todo)]
        [:span (str "Archived " (format-instant archived-at))])]]
    [:button.rounded-lg.border.border-slate-300.bg-white.px-3.py-2.text-sm.font-medium.text-slate-700.hover:border-slate-400.hover:text-slate-950
     {:type          "button"
      :data-on:click (str (set-signal "archived" false)
                          (post (routes/todo-archive (:todo/id todo))))}
     "Restore"]]])

(defn- filter-button [current-filter show-archived? value label]
  [:button.rounded-full.px-3.py-2.text-sm.font-medium
   {:type          "button"
    :data-on:click (str (set-signal "filter" value)
                        (set-signal "showArchived" show-archived?)
                        (post (routes/tab-state)))
    :class         (if (= current-filter value)
                     "bg-teal-600 text-white"
                     "bg-slate-100 text-slate-700 hover:bg-slate-200")}
   label])

(defn- show-archived-toggle [filter-k show-archived?]
  [:label.flex.items-center.gap-3
   [:input.h-5.w-5.rounded.border-slate-300.text-teal-600
    {:type           "checkbox"
     :checked        show-archived?
     :data-on:change (str (set-signal "filter" (current-filter-value filter-k))
                          (set-signal-expr "showArchived" "el.checked")
                          (post (routes/tab-state)))}]
   [:span.text-sm.font-medium.text-slate-700 "Show archived"]])

(defn- app-container
  [_req {:keys [session/user
               todo/ui-state
               app/show-admin-link?
               todo/items
               todo/archived-items
               todo/completed-count
               todo/archived-count
               todo/remaining-count]}]
  (let [show-archived? (:todo/show-archived ui-state)
        filter-k       (:todo/filter ui-state)]
    [:div#biff-datastar-content.space-y-8
     [:section.space-y-4
      [:div.flex.flex-wrap.items-center.justify-between.gap-4
       [:div.space-y-2
        [:p {:class "text-sm font-semibold uppercase text-teal-700 tracking-[0.2em]"} "Biff demo"]
        (ui/page-title "TodoMVC, but as a Biff smoke test")
        [:p.max-w-3xl.text-slate-600
         "This app exercises Biff auth, graph, fx, Datastar live updates, background jobs, admin, and SQLite-backed ownership rules in one place."]]
       [:div.flex.flex-wrap.items-center.gap-3
        (when show-admin-link?
          (ui/link {:href  "/_biff/admin"
                    :class "rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 no-underline hover:border-slate-400 hover:text-slate-950"}
                   "Admin dashboard"))
        (when show-admin-link?
          [:form {:data-on:submit (post (routes/todo-archive-batch))}
           [:button.rounded-lg.bg-slate-900.px-4.py-2.text-sm.font-medium.text-white.hover:bg-slate-700
            {:type "submit"}
            "Archive in batches of 3"]])
        [:form {:method "post"
                :action (routes/auth-signout)}
         (ui/button {:type "submit"} "Log out")]]]
      [:div.flex.flex-wrap.items-center.gap-2.text-sm.text-slate-600
       [:span "Signed in as"]
       [:strong.text-slate-950 (:user/email user)]
       [:span "\u00b7"]
       [:span (str "Current filter: " (filter-label filter-k))]
       (when show-archived?
         [:<>
          [:span "\u00b7"]
          [:span "Archived items visible"]])]]

     [:section.grid.gap-4.rounded-3xl.border.border-slate-200.bg-white.p-6.shadow-sm
      [:div.flex.flex-wrap.items-center.justify-between.gap-4
       [:div.flex.flex-wrap.items-center.gap-2
        (counter-pill "Remaining" remaining-count)
        (counter-pill "Completed" completed-count)
        (counter-pill "Archived" archived-count)]
       [:div.flex.flex-wrap.items-center.gap-2
        (filter-button filter-k show-archived? "all" "All")
        (filter-button filter-k show-archived? "active" "Active")
        (filter-button filter-k show-archived? "completed" "Completed")
        (show-archived-toggle filter-k show-archived?)]]

      [:form.grid.gap-3
       (merge {:data-on:submit (post (routes/todo-create))}
              (input-signal "newtodo" ""))
       [:label.text-sm.font-medium.text-slate-700
        "Add a todo"
        [:input.mt-2.w-full.rounded-2xl.border.border-slate-300.px-4.py-3.text-base.outline-none.ring-0.placeholder:text-slate-400.focus:border-teal-500
         {:type              "text"
          :name              "newtodo"
          :placeholder       "Open a second tab, then add something here"
          :required          true
          :data-bind:newtodo ""}]]
       [:div.flex.flex-wrap.items-center.justify-between.gap-3
        [:p.text-sm.text-slate-500
         "Every database write wakes the Datastar SSE stream, so other tabs update immediately."]
        [:button.rounded-2xl.bg-teal-600.px-4.py-3.text-sm.font-medium.text-white.hover:bg-teal-700
         {:type "submit"}
         "Create todo"]]]]

     [:section#current-todos-section.space-y-4
      [:div.flex.items-center.justify-between
       [:h2.text-xl.font-semibold.text-slate-950 "Current todos"]
       [:span.text-sm.text-slate-500 (str (count items) " visible")]]
      (if (seq items)
        [:div.grid.gap-3
         (for [todo items]
           [:div {:key (str (:todo/id todo))}
            (todo-item todo)])]
        [:div.rounded-2xl.border.border-dashed.border-slate-300.bg-slate-50.p-6.text-sm.text-slate-500
         "Nothing matches the current filter. Add a todo or reveal archived items."])]

     (when show-archived?
       [:section#archived-todos-section.space-y-4
        [:div.flex.items-center.justify-between
         [:h2.text-xl.font-semibold.text-slate-950 "Archived todos"]
         [:span.text-sm.text-slate-500 (str (count archived-items) " archived")]]
        (if (seq archived-items)
          [:div.grid.gap-3
           (for [todo archived-items]
             [:div {:key (str (:todo/id todo))}
              (archived-item todo)])]
          [:div.rounded-2xl.border.border-dashed.border-slate-300.bg-slate-50.p-6.text-sm.text-slate-500
           "No archived todos yet. Use the archive queue button to see background jobs kick in."])])]))

(defroute raw-app-page "/app"
  [:biff.graph.fx/query
   [{:session/user [:user/id :user/email :user/joined-at]}
    {:todo/ui-state model.todo/ui-state-fields}
    :app/show-admin-link?
    {:todo/items model.todo/todo-fields}
    {:todo/archived-items model.todo/todo-fields}
    :todo/active-count
    :todo/completed-count
    :todo/archived-count
    :todo/remaining-count]]

  :get
  (fn [req page-data]
    (let [container (app-container req page-data)]
      (if (:biff.datastar/sse-request req)
        (ui/html-response container)
        (ui/page
         {:title "Biff Demo App"}
         [:div.mx-auto.my-12.max-w-5xl.space-y-6
          (merge {:class "space-y-6"} biff.datastar/init-opts)
          container])))))

(defroute create-todo-route "/app/todos"
  :post
  (fn [{:keys [session biff.fx/now] :as req}]
    (if-some [title (trim-to-nil (param-value req :newtodo))]
      {:todo-diff      [:biff.sqlite.fx/authorized-write
                        {:insert-into :todo
                         :values      [{:todo/id         (random-uuid)
                                        :todo/user-id    (:uid session)
                                        :todo/title      title
                                        :todo/completed  false
                                        :todo/archived   false
                                        :todo/created-at now
                                        :todo/updated-at now}]}]
       :biff.fx/return (ui/signal-patch-response {"newtodo" ""})}
      {:biff.fx/return (ui/no-content)})))

(defroute tab-state-route "/app/tab-state"
  :post
  (fn [req]
    (let [tab-state-id (model.tab-state/tab-state-key req)
          tab-state    (when tab-state-id
                         (some-> (biff.sqlite/execute
                                  req
                                  {:select [:tab-state/data]
                                   :from   :tab-state
                                   :where  [:= :tab-state/id tab-state-id]})
                                 first
                                 :tab-state/data))]
      (update-ui-state! req tab-state)
      (ui/no-content))))

(defroute toggle-todo-route "/app/todos/:id/toggle"
  :post
  (fn [req]
    (toggle-todo! req)
    (ui/no-content)))

(defroute archive-todo-route "/app/todos/:id/archive"
  :post
  (fn [req]
    (archive-todo! req)
    (ui/no-content)))

(def module
  {:biff.ring/routes
   [["" {:middleware [mid/wrap-signed-in]}
     raw-app-page
     create-todo-route
     tab-state-route
     toggle-todo-route
     archive-todo-route]]})
