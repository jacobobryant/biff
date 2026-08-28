(ns com.biffweb.admin.impl.module
  (:require [com.biffweb.admin.impl.alerts :as alerts]
            [com.biffweb.admin.impl.metrics :as metrics]
            [com.biffweb.admin.impl.profiling :as profiling]
            [com.biffweb.admin.impl.resource :as resource]
            [com.biffweb.admin.impl.ui :as ui]
            [com.biffweb.admin.impl.users :as users]
            [com.biffweb.stuff :as stuff]))

(defn- wrap-config [handler config]
  (fn [ctx]
    (handler (merge ctx config))))

(defn routes [config]
  ["" {:middleware [[stuff/wrap-params]
                    [wrap-config config]]}
   ui/routes
   metrics/routes
   users/routes
   profiling/routes
   alerts/routes
   resource/routes])

(defn module [params]
  {:biff.core/init            (fn [_modules-var]
                                {:biff.admin/pstats (atom {})})
   :biff.background/tasks     [{:schedule profiling/hourly-schedule
                                :task     profiling/flush-pstats!}]
   :biff.graph/middleware     [profiling/wrap-resolver-profiling]
   :biff.ring/routes          (routes params)
   :biff.ring/base-middleware [profiling/wrap-profiling]})
