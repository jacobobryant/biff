(ns com.biffweb.fx
  "Effect system for Biff web applications.

   The core abstraction is `machine`: a state machine where each state
   is a pure function and effects happen while transitioning between
   states. This keeps application logic pure and testable.

   A machine is created with named state transition functions. Each
   function receives the context map and returns a map where:
     - Values that are vectors whose first element is a key in
       `:biff.fx/handlers` → that effect is
       executed, and the result is stored under the map entry's key in the
       context
     - :biff.fx/next → the next state to transition to
     - Other keys → merged into context for subsequent states

   Applications and libraries can extend effect dispatch with
   `:biff.fx/handlers`, or dynamically compute handlers via
   `:biff.fx/get-handlers`.
   :biff.fx/now is injected as a java.time.Instant before each state runs.
   :biff.fx/seed is injected as a random long seed.
   :biff.fx/results is set from the last trace entry."
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.fx.impl :as impl])
  (:import [java.util Random UUID]))

(biff.core/register
 {:biff.fx/fx-input     'map?
  :biff.fx/fx-output    'map?
  :biff.fx/get-handlers 'ifn?
  :biff.fx/handlers     [:map-of 'keyword? 'ifn?]
  :biff.fx/next         'keyword?
  :biff.fx/now          [:fn #(instance? java.time.Instant %)]
  :biff.fx/result       'any?
  :biff.fx/results      [:sequential 'map?]
  :biff.fx/return       'any?
  :biff.fx/seed         'integer?
  :biff.fx/state        'keyword?
  :biff.fx/state-output 'map?
  :biff.fx/trace        [:sequential 'map?]})

;; === Seed-based deterministic randomness ===

(defn uuid
  "Generate a UUID from a seed. Returns [uuid next-seed]."
  [seed]
  (let [rng (Random. seed)
        msb (.nextLong rng)
        lsb (.nextLong rng)]
    [(UUID. msb lsb) (.nextLong rng)]))

(defn random-bytes
  "Generate n random bytes from a seed. Returns [bytes next-seed]."
  [seed n]
  (let [rng (Random. seed)
        bs  (byte-array n)]
    (.nextBytes rng bs)
    [bs (.nextLong rng)]))

(def ^:private handlers-for-modules
  (memoize
   (fn [modules]
     (->> modules
          (keep :biff.fx/handlers)
          (apply merge {})))))

(defn module
  []
  {:biff.core/init
   (fn [modules-var]
     {:biff.fx/get-handlers
      (fn []
        (handlers-for-modules @modules-var))})})

;; === Core state machine ===

(defn machine
  "Creates a state machine handler. `machine-name` is used for error
   reporting. Remaining args are keyword/fn pairs defining the states.

   Usage:
     (machine ::my-handler
       :start (fn [ctx] ...)
       :next-state (fn [ctx] ...))

   The returned function can be called with:
     (handler ctx)        — runs from :start until no :biff.fx/next
     (handler ctx :state) — runs a single state (useful for testing)"
  [machine-name & {:as state->transition-fn}]
  (assert (contains? state->transition-fn :start)
          "machine must have a :start state")
  (fn run
    ([ctx state]
     ((or (get state->transition-fn state)
          (throw (ex-info (str "Invalid state: " state
                               " in machine " machine-name) {})))
      ctx))
    ([ctx]
     (let [handlers (merge impl/default-fx-handlers
                           (:biff.fx/handlers ctx)
                           (when-some [get-handlers (:biff.fx/get-handlers ctx)]
                             (get-handlers)))]
       (loop [ctx ctx, state :start, trace []]
         (let [{:keys [next-state ctx trace state-output fx-output]}
               (try
                 (impl/step {:state->transition-fn state->transition-fn
                             :ctx                  ctx
                             :state                state
                             :trace                trace
                             :handlers             handlers})
                 (catch Exception e
                   (throw
                    (ex-info "Exception while running biff.fx machine"
                             {:machine machine-name
                              :state   state
                              :trace   (impl/truncate trace)}
                             e))))]
           (if next-state
             (recur ctx next-state trace)
             (let [result (merge state-output
                                 (into {} (remove (fn [[k _]] (.startsWith (name k) "_"))) fx-output))]
               (if (contains? result :biff.fx/return)
                 (:biff.fx/return result)
                 result)))))))))

(defmacro defmachine
  "Defines a machine as a var. Machine name keyword is derived from
   the current namespace and the var symbol.

    Usage:
      (defmachine my-handler
        :start (fn [ctx] ...)
        :next-state (fn [ctx] ...))"
  [sym & args]
  (let [machine-name (keyword (str *ns*) (str sym))]
    `(def ~sym (machine ~machine-name ~@args))))
