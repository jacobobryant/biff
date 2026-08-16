(ns com.biffweb.background
  (:require [com.biffweb.background.impl :as impl]
            [com.biffweb.core :as biff.core]))

(def ^:private ? {:optional true})

(biff.core/register
 {:biff.background/job             [:map
                                    [:biff.background/priority ?]]
  :biff.background/jobs            [:sequential :biff.background/jobs]
  :biff.background/priority        :int
  :biff.background/queue           [:fn #(instance? java.util.AbstractQueue %)]
  :biff.background/queue-id        'qualified-keyword?
  :biff.background/queue-ids       [:sequential :biff.background/queue-id]
  :biff.background/queue-map       [:map
                                    [:consumer    'ifn?]
                                    [:queue     ? :biff.background/queue]
                                    [:n-threads ? 'pos-int?]
                                    [:executor  ? 'any?]
                                    [:state     ?
                                     [:fn #(instance? clojure.lang.IAtom %)]]]
  :biff.background/queue-state     [:map
                                    [:continue :boolean]
                                    [:processing 'set?]]
  :biff.background/queues          [:map-of
                                    :biff.background/queue-id
                                    :biff.background/queue-map]
  :biff.background/tasks           [:sequential :biff.background/task]
  :biff.background/task            [:map
                                    [:schedule        'ifn?]
                                    [:task            'ifn?]
                                    [:error-handler ? 'ifn?]
                                    [:on-finished   ? 'ifn?]]
  :biff.background/stop-timeout    :int})

(defn use-scheduled-tasks
  "Calls chime.core/chime-at for each `task`.

   Each task function receives ctx as its sole argument."
  {:arglists '([{:keys [biff.background/tasks] :as ctx}])}
  [ctx]
  (impl/use-scheduled-tasks ctx))

(defn use-queues
  "Initializes a queue and fixed executor thread pool for each entry in
   `queues`.

   See :biff.background/queues."
  {:arglists '([{:keys [biff.background/queues] :as ctx}])}
  [ctx]
  (impl/use-queues ctx))

(defn submit-jobs
  "Adds `jobs` to the specified queue.

   queue-id - :biff.background/queue-id
   jobs     - Sequence of :biff.background/job"
  {:arglists '([{:biff.background/keys [queues]} queue-id jobs])}
  [ctx queue-id jobs]
  (impl/submit-jobs ctx queue-id jobs))

(def fx-handlers
  "A biff.fx handlers map containing
   `:biff.background.fx/submit-jobs submit-jobs`"
  impl/fx-handlers)

(defn module
  "A biff.core module that:

   - Provides :biff.fx/handlers.
   - Aggregates :biff.background/tasks and :biff.background/queues from other
     modules. Tasks and queues are only aggregated on startup, not whenever
     modules change."
  []
  (impl/module))
