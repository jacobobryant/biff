(ns com.biffweb.demo.components
  (:require [com.biffweb.admin :as biff.admin]
            [com.biffweb.background :as biff.background]
            [com.biffweb.config :as biff.config]
            [com.biffweb.ring :as biff.ring]
            [com.biffweb.sqlite :as biff.sqlite]
            [tick.core :as tick]))

(defn- use-fake-pstats [ctx]
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

(defn- use-fake-errors [{:biff.admin/keys [alert-state] :as ctx}]
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

(def components
  [biff.config/use-aero-config
   use-fake-pstats
   biff.admin/use-alerts
   use-fake-errors
   biff.sqlite/use-sqlite
   biff.background/use-queues
   biff.background/use-scheduled-tasks
   biff.ring/use-jetty])
