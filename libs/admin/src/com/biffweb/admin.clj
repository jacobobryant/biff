(ns com.biffweb.admin
  (:require [com.biffweb.admin.impl.alerts :as alerts]
            [com.biffweb.admin.impl.module :as module]
            [com.biffweb.admin.impl.profiling :as profiling]
            [com.biffweb.core :as biff.core]))

(biff.core/register
 {:biff.admin/alert-email        'string?
  :biff.admin/alert-state        [:fn #(instance? clojure.lang.IAtom %)]
  :biff.admin/errors-atom        [:fn #(instance? clojure.lang.IAtom %)]
  :biff.admin/get-revenue-events 'ifn?
  :biff.admin/get-user-events    'ifn?
  :biff.admin/get-users          'ifn?
  :biff.admin/pstats             [:fn #(instance? clojure.lang.IAtom %)]
  :biff.admin/revenue-event      [:map
                                  [:instant 'inst?]
                                  [:revenue 'number?]]
  :biff.admin/send-email         'ifn?
  :biff.admin/user               [:map
                                  [:user-id 'some?]
                                  [:email {:optional true} 'string?]
                                  [:joined-at {:optional true} 'inst?]]
  :biff.admin/user-event         [:map
                                  [:user-id 'some?]
                                  [:instant 'inst?]]
  :biff.admin/user-id            'some?})

(defn profile! [ctx id f]
  (profiling/profile! ctx id f))

(defn wrap-profiling [handler]
  (profiling/wrap-profiling handler))

(defn wrap-resolver-profiling [resolver]
  (profiling/wrap-resolver-profiling resolver))

(defn flush-pstats! [ctx]
  (profiling/flush-pstats! ctx))

(defn routes [options]
  (module/routes options))

(defn module [params]
  (module/module params))

(defn use-alerts [ctx]
  (alerts/use-alerts ctx))
