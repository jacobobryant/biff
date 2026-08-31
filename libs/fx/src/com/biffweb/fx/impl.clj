(ns com.biffweb.fx.impl
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [com.biffweb.core :as biff.core])
  (:import [java.time Instant]
           [java.util Random UUID]))

(biff.core/register
 {::state->fn [:and
               [:map-of :keyword 'ifn?]
               [:map
                [:start 'ifn?]]]})

(def ^:private default-fx-handlers
  {:biff.fx/http
   (fn [_ctx input]
     (let [hato-request (requiring-resolve 'hato.client/request)
           http*        (fn [request]
                          (try
                            (-> (hato-request request)
                                (assoc :url (:url request)))
                            (catch Exception e
                              (if (get request :throw-exceptions true)
                                (throw e)
                                {:url       (:url request)
                                 :exception e}))))]
       (cond
         (nil? hato-request)
         (throw (ex-info (str "To use :biff.fx/http, you must add hato to your "
                              "dependencies.")
                         {}))

         (map? input) (http* input)
         (sequential? input) (mapv http* input)
         :else (throw (ex-info "Invalid input type for :biff.fx/http"
                               {:type (type input)})))))})

(defn- truncate-str [s n]
  (if (<= (count s) n) s (str (subs s 0 (dec n)) "…")))

(defn- truncate [data]
  (walk/postwalk
   #(if (string? %) (truncate-str % 500) %)
   data))

(defn uuid4 [seed]
  (let [rng       (Random. seed)
        msb0      (.nextLong rng)
        lsb0      (.nextLong rng)
        ;; Set version to 4
        msb       (-> msb0
                      (bit-and (unchecked-long 0xffffffffffff0fff))
                      (bit-or  (long 0x4000)))
        ;; Set RFC 4122 variant
        lsb       (-> lsb0
                      (bit-and (unchecked-long 0x3fffffffffffffff))
                      (bit-or  Long/MIN_VALUE))
        next-seed (.nextLong rng)]
    [(UUID. msb lsb) next-seed]))

(defn uuid7 [seed instant]
  (let [rng       (Random. seed)
        ts        (bit-and (inst-ms instant) 0xffffffffffff)
        rand-a    (bit-and (.nextInt rng) 0x0fff)
        rand-b    (.nextLong rng)
        msb       (unchecked-long
                   (bit-or (bit-shift-left ts 16)
                           (bit-shift-left 0x7 12)
                           rand-a))
        lsb       (-> rand-b
                      (bit-and (unchecked-long 0x3fffffffffffffff))
                      (bit-or Long/MIN_VALUE))
        next-seed (.nextLong rng)]
    [(UUID. msb lsb) next-seed]))

(defn- effect-vector? [handlers x]
  (and (vector? x)
       (contains? handlers (first x))))

(defn- uuid-seq [f seed]
  (lazy-seq
   (let [[uuid next-seed] (f seed)]
     (cons uuid (uuid-seq f next-seed)))))

(defn- step [{:keys [machine-name state->fn handlers ctx]}
             {:keys [state input trace]}]
  (let [log-ctx       {:biff.fx/state        state
                       :biff.fx/machine-name machine-name
                       :biff.fx/trace        trace}
        error!        (fn [message extra ex]
                        (throw (ex-info message
                                        (truncate (merge log-ctx extra))
                                        ex)))
        handler-error "Handler function threw an exception"
        state-fn      (or (get state->fn state)
                          (error! "Invalid state"
                                  {:biff.fx/available-states (keys state->fn)}
                                  nil))
        now           (Instant/now)
        rng           (Random.)

        [seed uuid7-seed uuid4-seed] (repeatedly 3 #(.nextLong rng))

        injected   {:biff.fx/now              now
                    :biff.fx/seed             seed
                    :biff.fx/random-uuid7-seq (uuid-seq #(uuid7 % now)
                                                        uuid7-seed)
                    :biff.fx/random-uuid4-seq (uuid-seq uuid4 uuid4-seed)}
        raw-result (try
                     (apply state-fn (merge ctx injected) input)
                     (catch Exception e
                       (error! "State function threw an exception"
                               injected e)))
        result     (if (map? raw-result)
                     raw-result
                     {:biff.fx/return raw-result})

        evaluate-effects
        (fn [result]
          (let [effect-keys
                (filterv (fn [k]
                           (effect-vector? handlers (get result k)))
                         (keys result))]
            (into (apply dissoc result effect-keys)
                  (keep (fn [k]
                          (let [[handler-key & args] (get result k)

                                handler-result
                                (try
                                  (apply (get handlers handler-key)
                                         ctx
                                         args)
                                  (catch Exception e
                                    (error!
                                     handler-error
                                     {:biff.fx/output       result
                                      :biff.fx/handler-args args}
                                     e)))]
                            (when-not (str/starts-with? (str k) ":_")
                              [k handler-result])))
                        effect-keys))))

        seq-output
        (mapv (fn [result]
                (cond
                  (map? result) (evaluate-effects result)

                  (effect-vector? handlers result)
                  (evaluate-effects {:_ignored result})

                  :else
                  (error! "Invalid :biff.fx/seq element"
                          {:biff.fx/element result}
                          nil)))
              (:biff.fx/seq result))

        output (evaluate-effects (dissoc result :biff.fx/seq))
        output (apply merge (concat seq-output [output]))]
    output))

(defn- initial-handler [handlers handler-key machine-name]
  (or (get handlers handler-key)
      (throw
       (ex-info "Invalid initial effect handler"
                {:biff.fx/handler            handler-key
                 :biff.fx/machine-name       machine-name
                 :biff.fx/available-handlers (keys handlers)}))))

(defn machine [machine-name & args]
  (let [[initial-fx args] (if (vector? (first args))
                            [(first args) (rest args)]
                            [nil args])
        state->fn         (if (and (= 1 (count args)) (map? (first args)))
                            (first args)
                            (apply hash-map args))]
    (when-not (or (nil? initial-fx)
                  (keyword? (first initial-fx)))
      (throw (ex-info "Initial effect must be a vector starting with a keyword."
                      {:biff.fx/initial-fx   initial-fx
                       :biff.fx/machine-name machine-name})))
    (biff.core/validate {::state->fn state->fn})
    (fn run [& run-args]
      (if (empty? run-args)
        state->fn
        (let [[{:keys [biff.fx/get-handlers] :as ctx} & args] run-args

              handlers (merge default-fx-handlers
                              (:biff.fx/handlers ctx)
                              (when get-handlers
                                (get-handlers)))
              _        (biff.core/validate {:biff.fx/handlers handlers})

              initial-result
              (when initial-fx
                (let [handler      (initial-handler handlers
                                                    (first initial-fx)
                                                    machine-name)
                      handler-args (rest initial-fx)]
                  [(apply handler ctx handler-args)]))

              opts {:machine-name machine-name
                    :state->fn    state->fn
                    :handlers     handlers
                    :ctx          ctx}]
          (loop [state :start
                 input (concat initial-result args)
                 trace []]
            (let [output (biff.core/validate
                          (step opts
                                {:state state
                                 :input input
                                 :trace trace}))]
              (cond
                (:biff.fx/next output)
                (do
                  (assert (not (contains? output :biff.fx/return))
                          (str "You can't set :biff.fx/next and "
                               ":biff.fx/return at the same time."))
                  (recur (:biff.fx/next output)
                         [output]
                         (conj trace output)))

                (contains? output :biff.fx/return)
                (:biff.fx/return output)

                :else output))))))))

(defmacro defmachine [sym & args]
  (let [machine-name (keyword (str *ns*) (str sym))]
    `(def ~sym (machine ~machine-name ~@args))))

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
      #(handlers-for-modules @modules-var)})})
