(ns com.biffweb.core
  "biff.core defines the interfaces and code that connect all the other Biff libs.

   SCHEMA

   :biff.core/init
   (fn [modules-var]) => {...}

     See biff.core/start.


   :biff.core/stop
   (fn []) => nil

     See biff.core/start and biff.core/stop.


   :biff.core/secret
   A Delay-like value returned by biff.core/secret-delay

     Meant to be used as a schema value:

       (biff.core/register {:com.example/api-key :biff.core/secret})


   :biff.core/kv-set
   (fn [ctx namespace key value]) => nil

     Sets the given key in namespace to value. A nil value should delete the
     key, i.e. a call to :biff.core/kv-list should only return keys with non-nil
     values.

     namespace
       Qualified keyword, e.g. :com.example/things. The keyword's namespace
       should be owned by whatever library or application defines the keyword.

     key
       String.

     value
       Any Clojure value that can be round-tripped through pr-str ->
       clojure.edn/read-string without custom options.


   :biff.core/kv-get
   (fn [ctx namespace key]) => value

     Returns the value for the given key in namespace. If key is unset, returns
     nil. See :biff.core/kv-set.


   :biff.core/kv-list
   (fn [ctx namespace]),
   (fn [ctx namespace key-prefix]), => [key1, key2, ...]

     Returns a sequence of sorted keys in the given namespace. See
     :biff.core/kv-set.

     key-prefix
       String. If set, returns only the keys beginning with this prefix."
  (:require [com.biffweb.core.impl.system :as impl.sys]
            [com.biffweb.core.impl.secrets :as impl.sec]
            [com.biffweb.core.impl.validation :as impl.v]))

(impl.v/register {:biff.core/init    'fn?
                  :biff.core/stop    [:vector 'fn?]
                  :biff.core/secret  [:fn delay?]
                  :biff.core/kv-set  'fn?
                  :biff.core/kv-get  'fn?
                  :biff.core/kv-list 'fn?})

(defn start
  "Starts a Biff application and returns the system map.

     (def modules [...])
     (def components [...])
     (biff.core/start #'modules components)

   Calls any :biff.core/init functions in modules and merges the results in
   order to create an initial system map. If you passed initial-system, it will
   be merged into this system map. Then the system map is passed through each
   component.

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

   Each component is a function that receives the system map, starts stateful
   resources or does other initialization as needed, and returns an updated
   system map. Components can add stop functions (zero-arg functions that stop a
   stateful resource) to the end of the :biff.core/stop vector. :biff/stop is
   also recognized for backwards compatibility.

   Uses biff.core/validate to ensure that keys in modules, keys returned by
   :biff.core/init, and keys returned by components are valid."
  ([modules-var components]
   (impl.sys/start modules-var components))
  ([initial-system modules-var components]
   (impl.sys/start initial-system modules-var components)))

(defn stop
  "Stops a Biff application.

   Calls the :biff.core/stop functions from system in reverse order."
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
   schemas for their key. Returns m.

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
  (impl.sec/secret-delay x))
