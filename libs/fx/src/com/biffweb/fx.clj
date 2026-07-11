(ns com.biffweb.fx
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.fx.impl :as impl]))

(biff.core/register
 {:biff.fx/get-handlers 'ifn?
  :biff.fx/handlers     [:map-of :keyword 'ifn?]
  :biff.fx/next         :keyword
  :biff.fx/now          'inst?
  :biff.fx/seed         :int})

(defn machine
  "Returns a function that runs your code as a state machine.

   state->fn
     A map from state keywords to state functions. Must include :start.
     Functions return a map describing effects to execute and (optionally) which
     state to transition to.

   machine-name
     An identifier (string, symbol, keyword...) that will be included in ex-data
     for any exceptions thrown by your state functions or handler functions.

   Returns (fn [ctx & [state]]).

   Example (see defmachine):

     (def handlers {:example.fx/http (fn [ctx request] ...)})
     (def ctx {:biff.fx/handlers handlers})

     (defmachine my-machine
       :start
       (fn [ctx]
         {:response     [:example.fx/http {:url ...}]
          :biff.fx/next :process})

       :process
       (fn [{:keys [response]}]
         {:result (process-response response)}))

     (my-machine ctx)
     => {:result ...}"
  [machine-name & {:as state->fn}]
  (impl/machine machine-name state->fn))

(defmacro defmachine
  "Defines a var containing an fx machine. Constructs machine-name from the
   given symbol and the current namespace.

   See com.biffweb.fx/machine."
  {:arglists '([sym & {:as state->fn}])}
  [sym & args]
  `(impl/defmachine ~sym ~@args))

(defn module
  "A biff.core module that collects :biff.fx/handlers from other modules.

   Includes an init function that sets :biff.fx/get-handlers on the system map."
  []
  (impl/module))

(defn uuid4
  "Deterministically generates a v4 (random) UUID.

   seed
     a long, e.g. the :biff.fx/seed value injected by `machine`.

   Returns [uuid next-seed].

   For subsequent RNG operations, be sure to use next-seed instead of the seed
   you passed to this function."
  [seed]
  (impl/uuid4 seed))

(defn uuid7
  "Deterministically generates a v7 (sequential random) UUID.

   seed
     a long, e.g. the :biff.fx/seed value injected by `machine`.

   Returns [uuid next-seed].

   For subsequent RNG operations, be sure to use next-seed instead of the seed
   you passed to this function."
  [seed instant]
  (impl/uuid7 seed instant))
