(ns com.biffweb.background
  (:require [com.biffweb.background.impl :as impl]
            [com.biffweb.core :as biff.core]))

(def ^:private ? {:optional true})

(biff.core/register
 {:biff.background/job          'map?
  :biff.background/jobs         [:sequential 'map?]
  :biff.background/priority     :int
  :biff.background/queue        [:fn #(instance? java.util.AbstractQueue %)]
  :biff.background/queue-id     'qualified-keyword?
  :biff.background/queue-ids    [:sequential :biff.background/queue-id]
  :biff.background/queue-map    [:map
                                 [:consumer    'ifn?]
                                 [:queue     ? :biff.background/queue]
                                 [:n-threads ? 'pos-int?]
                                 [:executor  ? 'any?]
                                 [:state     ?
                                  [:fn #(instance? clojure.lang.IAtom %)]]]
  :biff.background/queue-state  [:map
                                 [:continue :boolean]
                                 [:processing 'set?]]
  :biff.background/queues       [:map-of
                                 :biff.background/queue-id
                                 :biff.background/queue-map]
  :biff.background/submit-jobs  'ifn?
  :biff.background/tasks        [:sequential :biff.background/task]
  :biff.background/task         [:map
                                 [:schedule        'ifn?]
                                 [:task            'ifn?]
                                 [:error-handler ? 'ifn?]
                                 [:on-finished   ? 'ifn?]]
  :biff.background/stop-timeout :int})

(defn use-scheduled-tasks [ctx]
  (impl/use-scheduled-tasks ctx))

(defn use-queues [ctx]
  (impl/use-queues ctx))

(defn submit-jobs [ctx queue-id jobs]
  (impl/submit-jobs ctx queue-id jobs))

(def fx-handlers
  impl/fx-handlers)

(defn module []
  (impl/module))
