(ns com.biffweb.fx
  "Remove effects from your application logic with state machines.

   SCHEMA

   :biff.fx/handlers
   {:com.example/do-something (fn [ctx & args]),
    ...}

     A map from effect keywords to handler functions.


   :biff.fx/get-handlers
   (fn []) => {...}

     A function that returns a :biff.fx/handlers map. Intended for use by
     :biff.core/init functions.


   :biff.fx/next
   keyword

     A key in the state->fn map passed to biff.fx/machine.


   :biff.fx/now
   instant

     The current time.


   :biff.fx/seed
   long

     A random seed. Use it like (java.util.Random. seed)"
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
     => {:result ...}

   If the returned function is called with a single argument (ctx), the :start
   state function is called first, and then the machine transitions to other
   states until completion. Effects are executed after each state function runs.
   If the function is also called with the second `state` argument (a keyword),
   the associated state function will be called with no transitions or effects
   (useful for unit testing).

   Each state funtion is called with a single argument: the ctx map, merged with
   output from the previous state function (see below), with fresh :biff.fx/now
   and :biff.fx/seed keys injected. Each state function must return either a map
   or a sequence of maps.

   Values in those maps that represent effects (\"effect descriptors\") are then
   replaced with the results of their associated effect handler functions. If
   the state function returned a sequence of maps, those maps are updated in
   order and then merged together as the \"output.\"

   An effect descriptor is a vector whose first element is a key from your
   handlers map (e.g. `[:com.example/http ...]`). Effect descriptors are only
   recognized when they are the top-level values of the returned map.

   The handlers map is constructed by merging in this order (1) a default
   handlers map provided by biff.fx which contains a single :biff.fx/http
   handler, (2) the value of :biff.fx/handlers from ctx, (3) the return value of
   calling the :biff.fx/get-handlers function from ctx, if set.

   Each value in the handlers map must be a function which takes at least one
   argument (ctx) and any number of additional arguments. It performs an effect
   and returns whatever value is appropriate. biff.fx will inject the ctx
   argument, and the remaining arguments are taken from the effect descriptor.

   When calling a handler function, ctx is merged with the \"output so far\"
   from the current state function. If the state function returned a map, this
   is the output map with any effect descriptors (and their associated keys)
   removed. If the state function returned a sequence of maps, this includes the
   output map that's currently being evaluated (without effect descriptors)
   merged with all the previous maps (including the effect results).

   If the state function includes `:biff.fx/next <state keyword>` in its output,
   that state is transitioned to next. Otherwise the output is returned as the
   final return value. If you want the machine to return something other than a
   map, you can include `:biff.fx/return <value>` in the output, in which case
   `<value>` will be the final return value. It's invalid to set both
   :biff.fx/next and :biff.fx/return in the same output map.

   To use the default :biff.fx/http handler, you must add Hato to your
   dependencies. The handler calls hato.client/request. It accepts either a
   single request map or a sequence of request maps, and it returns either a
   single response map or a vector of response maps.

     [:biff.fx/http [request1 request2 ...]]

   The response map(s) includes :url from the request. If you set
   `:throw-exceptions true` on the request, biff.fx will catch exceptions and
   return {:url ..., :exception ...} if there is one."
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
