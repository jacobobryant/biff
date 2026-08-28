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

   An initial effect descriptor may be placed before the state definitions. Its
   result is passed to :start immediately after ctx.

   machine-name
     An identifier (string, symbol, keyword...) that will be included in ex-data
     for any exceptions thrown by your state functions or handler functions.

   Returns (fn [ctx & args]). The :start function receives ctx followed by the
   machine arguments. Other states receive ctx and the previous output map. Set
   (:biff.fx/test ctx) to a state keyword to call one state without evaluating
   effects.

   Example (see defmachine):

     (def handlers {:example.fx/http (fn [ctx request] ...)})
     (def ctx {:biff.fx/handlers handlers})

     (defmachine my-machine
       :start
       (fn [ctx]
         {:response     [:example.fx/http {:url ...}]
          :biff.fx/next :process})

       :process
       (fn [ctx {:keys [response]}]
         {:result (process-response response)}))

     (my-machine ctx)
     => {:result ...}"
  [machine-name & args]
  (apply impl/machine machine-name args))

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
