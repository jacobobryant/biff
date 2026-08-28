(ns com.biffweb.demo.modules
  (:require [com.biffweb.admin :as biff.admin]
            [com.biffweb.background :as biff.background]
            [com.biffweb.config :as biff.config]
            [com.biffweb.datastar :as biff.datastar]
            [com.biffweb.demo.app.admin :as admin]
            [com.biffweb.demo.app.archive :as archive]
            [com.biffweb.demo.app.auth :as auth]
            [com.biffweb.demo.app.landing :as landing]
            [com.biffweb.demo.app.todos :as todos]
            [com.biffweb.demo.model.schema :as schema]
            [com.biffweb.demo.model.tab-state :as model.tab-state]
            [com.biffweb.demo.model.todo :as model.todo]
            [com.biffweb.demo.model.user :as model.user]
            [com.biffweb.fx :as biff.fx]
            [com.biffweb.graph :as biff.graph]
            [com.biffweb.ring :as biff.ring]
            [com.biffweb.sqlite :as biff.sqlite]
            [tick.core :as tick]))

(defn- start-fake-pstats [ctx]
  (doseq [[method path count] [[:get "/" 80]
                               [:get "/app" 60]
                               [:get "/api/todos" 40]
                               [:post "/api/todos" 20]]
          _                   (range count)]
    ((biff.admin/wrap-profiling
      (fn [_]
        (reduce + (range (rand-int 1000)))))
     (assoc ctx
            :request-method method
            :reitit.core/match {:template path})))
  ctx)

(defn- start-fake-errors [{:biff.admin/keys [alert-state] :as ctx}]
  (swap! alert-state assoc :errors
         (mapv (fn [index]
                 (let [message (nth ["Database connection timed out"
                                     "Payment webhook could not be processed"
                                     "Background job failed"
                                     "Unexpected response from email provider"]
                                    (mod index 4))]
                   {:instant (tick/<<
                              (tick/now)
                              (tick/new-duration (* index 3) :hours))
                    :message message

                    :stack-trace
                    (str "clojure.lang.ExceptionInfo: " message "\n"
                         "\tat com.biffweb.demo.example$run.invoke"
                         "(example.clj:" (+ 20 index) ")\n"
                         "\tat clojure.lang.AFn.applyToHelper"
                         "(AFn.java:154)")}))
               (range 12)))
  ctx)

(def fake-pstats-module
  {:biff.core/id    :com.biffweb.demo/fake-pstats
   :biff.core/start start-fake-pstats})

(def fake-errors-module
  {:biff.core/id    :com.biffweb.demo/fake-errors
   :biff.core/start start-fake-errors})

(def modules
  [(biff.config/module)
   (biff.ring/module)
   (biff.datastar/module)
   (biff.background/module)
   (biff.fx/module)
   (biff.graph/module)
   (biff.sqlite/module)
   fake-pstats-module
   fake-errors-module
   model.user/module
   model.tab-state/module
   model.todo/module
   schema/module
   admin/module
   landing/module
   auth/module
   archive/module
   todos/module])
