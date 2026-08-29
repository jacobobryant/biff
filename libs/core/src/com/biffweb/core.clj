(ns com.biffweb.core
  (:require [com.biffweb.core.impl.system :as impl.sys]
            [com.biffweb.core.impl.validation :as impl.v]
            [com.biffweb.stuff.secret :as stuff.secret]))

(impl.v/register
 {:biff.core/init             'fn?
  :biff.core/start            'fn?
  :biff.core/id               'qualified-keyword?
  :biff.core/stop             'fn?
  :biff.core/stop-system      'fn?
  :biff.core/secret           [:or [:fn delay?] :string]
  :biff.core/kv-set           'fn?
  :biff.core/kv-get           'fn?
  :biff.core/kv-list          'fn?
  :biff.core/kv-namespace     'qualified-keyword?
  :biff.core/kv-key           'string?
  :biff.core/kv-prefix        [:maybe 'string?]
  :biff.core/wrap-db-snapshot 'ifn?
  :biff.core/on-tx            'ifn?})

(defn component-shim
  "Converts a Biff 1 component into a Biff 2 module with lifecycle functions.

     (component-shim :com.example/use-jetty biff/use-jetty)

   The returned module's ID is `id`. Its start function calls `component-fn`,
   and its stop function calls the functions that `component-fn` added to
   :biff/stop."
  [id component-fn]
  (impl.sys/component-shim id component-fn))

(defn start
  "Starts a Biff application and returns the system map.

     (def modules [...])
     (def start-order [...])
     (biff.core/start #'modules start-order)

   Calls any :biff.core/init functions in modules and merges the results in
   order to create an initial system map. If you passed initial-system, it will
   be merged into this system map. Then the system map is passed through each
   :biff.core/start function.

   :biff.core/init is a function that receives the modules var, aggregates keys
   from other modules and/or initializes other values as needed, and returns the
   results as part of a partial system map. For values that should be updated
   when modules-var is updated (without restarting the system), :biff.core/init
   can wrap them with a function that's memoized based on the value of
   modules-var:

     (def get-foo
       (memoize
        (fn [modules]
          (aggregate-foos
           (keep :example/foo modules)))))

     {:biff.core/init
      (fn [modules-var]
        {:example/get-foo #(get-foo @modules-var)})}

   Includes a default init function which defines a :biff.core/on-tx function
   that calls :biff.core/on-tx from the other modules in a doseq.

   Entries in start-order must be qualified module ID keywords. For each module
   ID, there must be a module with :biff.core/id set to the keyword and with
   :biff.core/start set to a function like `(fn [ctx]) -> ctx`. :biff.core/stop
   may be set to a function like `(fn [ctx])`.

   The start function can start stateful resources or do other initialization as
   needed, returning an updated system map. The stop function receives the
   return value of the start function and shuts down stateful resources etc as
   needed. biff.core adds a zero-argument :biff.core/stop-system function to the
   system map which calls the module stop functions in reverse startup order.

   Uses biff.core/validate to ensure that keys in modules, keys returned by
   :biff.core/init, and keys returned by start functions are valid."
  ([modules-var start-order]
   (impl.sys/start modules-var start-order))
  ([initial-system modules-var start-order]
   (impl.sys/start initial-system modules-var start-order)))

(defn stop
  "Stops a Biff application.

   Calls the :biff.core/stop-system function from system."
  [system]
  (impl.sys/stop system))

(defn register
  "Merges a map of Malli schemas into Biff's global schema registry.

     (biff.core/register {:person/display-name :string
                          :person/age          :int})

   Registered schemas are used by biff.core/validate."
  [schemas]
  (impl.v/register schemas))

(defn get-registry
  "Returns all schemas that have been passed to biff.core/register.

     (biff.core/get-registry)
     => {:person/display-name :string
         :person/age          :int}"
  []
  (impl.v/get-registry))

(defmacro validate
  "Throws an AssertionError if any values in m don't match the registered
   schemas for their key. Returns m. When *assert* is false, compiles to a
   no-op.

     (biff.core/register {:person/age :int})
     (biff.core/validate {:person/age \"three\"})
     ; =>
     ; (err) `:person/age \"three\"` is invalid: should be an integer

   :required
     A sequence of keys. Throws an error if any of these aren't present in m.

   :extra-schema
     A map of Malli schemas. Can be used to define schema without modifying the
     global registry by calling biff.core/register.

   :error-data
     Data that will be serialized into the error message.

   For convenience, m can be a sequence of maps instead of a single map."
  {:arglists '([m & {:keys [required extra-schema]}])}
  [& args]
  `(impl.v/validate ~@args))

(defn validate-with-ex
  "Like `validate` but ignores *assert* and throws an ExceptionInfo.

   Intended for cases where you want to ensure that validation runs in
   production."
  {:arglists '([m & {:keys [required extra-schema]}])}
  [m-or-seq & opts]
  (apply impl.v/validate-with-ex m-or-seq opts))

(defn secret-delay
  "Wraps x in a Delay that doesn't show its value when serialized.

     (def my-api-key (secret-delay \"my-api-key\"))
     (str my-api-key)
     => \"#<SecretDelay: redacted>\"

     (force my-api-key)
     => \"my-api-key\"

   force is recommended for unwrapping secrets (instead of deref / @) so that
   nil values don't cause an exception.

   For backwards compatibility, the returned value can also be unwrapped by
   calling it as a function:

     (my-api-key)
     => \"my-api-key\"

   Biff libraries expect secrets to be wrapped like this to help prevent leaking
   secrets (e.g. in logs).

   Note that this is a function, not a macro, and thus the arguments are
   evaluated immediately, unlike a normal Delay."
  [x]
  (stuff.secret/secret-delay x))

;; technically removing this would be a breaking change--might do it right
;; before the non-prerelease
(def ^:no-doc module {})
