# biff.fx

Remove effects from your application logic with state machines.

biff.fx lets you structure your code as a state machine where the states do pure
computation (your application logic) and effects are executed during the
transitions (HTTP requests, database queries, etc).

This makes your code more readable and testable since your application logic is
contained entirely within pure functions.

### Dependency

```clojure
com.biffweb/fx {:mvn/version "2.0.0-rc4"}
```

### Status

This library will be a release candidate until all [the other Biff 2
libraries](/README.md) have been released. Until then there could be breaking
changes, but I don't anticipate any.

## API Reference

[com.biffweb.fx](/docs/api/com.biffweb.fx.md)

In particular see the [machine](/docs/api/com.biffweb.fx.md#machine) docstring
which is comprehensive.

## Example

This code snippet shows how to write a function that reads a file containing a
number, increments that number, and writes the new number back to the file,
returning the new number.

```clojure
(require '[com.biffweb.fx :as biff.fx :refer [defmachine]])
(require '[clojure.java.io :as io])

(def handlers
  {:example.fx/slurp (fn [_ctx path]
                       (let [file (io/file path)]
                         (when (.exists file)
                           (slurp file))))
   :example.fx/spit  (fn [_ctx path content]
                       (spit path content))})

(def ctx
  {:biff.fx/handlers handlers})

(defmachine increment-file
  :start
  (fn [{:keys [path]}]
    {:content      [:example.fx/slurp path]
     :biff.fx/next :increment})

  :increment
  (fn [{:keys [path content]}]
    (let [n     (or (some-> content parse-long) 0)
          new-n (inc n)]
      {:_              [:example.fx/spit path (str new-n)]
       :biff.fx/return new-n})))

(increment-file (merge ctx {:path "number.txt"}))
=> 1
(increment-file (merge ctx {:path "number.txt"}))
=> 2
```

Now you can write simple `(is (= (f x) y))` unit tests:

```
(require '[clojure.test :refer [deftest is]])

(deftest increment-file-tests
  (is (= (increment-file {:path "number.txt"}
                         :start)
         {:content       [:example.fx/slurp "number.txt"],
          :biff.fx/next :increment}))
  (is (= (increment-file {:path "number.txt" :content "2"}
                         :increment)
         {:_              [:example.fx/spit "number.txt" "3"],
          :biff.fx/return 3})))
```

## Terms

**Machine**: a function defined with `biff.fx/defmachine` or `biff.fx/machine`,
such as `increment-file` from the example.

**Effect handlers** and **effect keywords**: functions that perform effects and
their associated keywords, such as `:example.fx/slurp (fn ...)` from the
example.

**State functions** and **state keywords**: pure functions that contain your
application logic and their associated keywords, such as `:increment (fn ...)`
from the example.

**Effect descriptor**: a vector that describes an effect handler invokation,
such as `[:example.fx/slurp path]` from the example. The first element is an
effect keyword and the remaining elements are positional arguments for the
effect handler.

**Output map**: a map returned by a state function.

## Usage

First you need to define a map containing all the effect handlers your
application needs to perform, such as http requests, database
queries/transactions, etc. These functions should be as simple as possible: take
some input, execute an effect, return the output.

```clojure
(def handlers
  {:example.fx/http (fn [_ctx request]
                      (http/request request))
   ...})
```

You'll need to pass this handlers map to your machine functions under the
`:biff.fx/handlers` key. A convenient way to do that is to insert that key into
incoming Ring requests, and then your Ring handlers can be defined with
`defmachine`.

```clojure
(def fx-handlers ...)

(defn wrap-fx-handlers [handler]
  (fn [request]
    (handler (merge request {:biff.fx/handlers fx-handlers}))))

(defmachine my-ring-handler
  :start
  (fn [request]
    ...)

  :response
  (fn [request]
    {:status 200,
     ...}))

(def routes
  ["/my-page" {:get my-ring-handler}])
```

Each machine function defines its own set of states, which must include at least
a `:start` state since that runs first. State functions typically return maps.
When you need to perform an effect, you can set one of the top-level keys in
that output map to an effect descriptor and set the `:biff.fx/next` key to a
state keyword. biff.fx will replace effect descriptors with the return values of
the effect handlers they reference, and then biff.fx will pass that updated
output map to the next state function.

```clojure
:start
(fn [ctx]
  {:result       [:example.fx/do-something 1 2 3]
   :biff.fx/next :process-result})

:process-result
(fn [{:keys [result]}]
  ...)
```

If you don't set `:biff.fx/next`, then after performing effects, the output map
will be used as the machine function's return value.

### Testing

You can call an individual state function, without any of the effect-handling or
state-transitioning logic, by passing a state keyword as the second param to the
machine function.

```clojure
(my-machine ctx :start)
=> {...}
```

## Tips

- If you want your machine function to return something other than a map, you
  can set the `:biff.fx/return` key in the output map. Its value will become
  the machine's return value. Note that it's invalid to set both `:biff.fx/next`
  and `:biff.fx/return`.

- If there are multiple effect descriptors in an output map, their order of
  execution is not specified. If you need to execute effects in a particular
  order, the recommended approach is to have your effect handler accept a
  sequence of inputs and return a sequence of outputs. If that doesn't work
  (e.g. you need to perform two different kinds of effects in a particular
  order), your state function can return a sequence of output maps. The maps
  will be processed in order and then merged into a single output map.

- In some situations you may not need more than a single `:start` state, e.g. a
  POST request handler that writes a value to the database and returns a 200
  response unconditionally.

- As a convention, if you don't need to use the return value of an effect
  handler, you can set the effect descriptor on an underscore-prefixed key like
  `:_` or `:_response`.

- `machine` and `defmachine` can both take a single `state->fn` map instead of
  key-value var args. This can be useful for defining multiple machines with
  similar logic since you can e.g. use a helper function that returns the
  `state->fn` map:

```clojure
(defn make-machine [{:keys [message]}]
  {:start   ...
   :process ...})

(defmachine hello
  (make-machine {:message "hello"}))

(defmachine goodbye
  (make-machine {:message "goodbye"}))
```
