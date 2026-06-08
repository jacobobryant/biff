(ns com.biffweb.fx.impl
  (:require [clojure.walk :as walk]
            [com.biffweb.core :as biff.core])
  (:import [java.time Instant]
           [java.util Random UUID]))

(biff.core/register
 {::state->fn       [:and
                     [:map-of :keyword 'ifn?]
                     [:map
                      [:start 'ifn?]]]
  ::state-fn-result [:or [:maybe 'map?] [:sequential [:maybe 'map?]]]})

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

(defn- step [{:keys [machine-name state->fn handlers ctx]}
             {:keys [state input trace]}]
  (let [log-ctx     {:biff.fx/state        state
                     :biff.fx/machine-name machine-name
                     :biff.fx/trace        trace}
        error!      (fn [message extra ex]
                      (throw (ex-info message
                                      (truncate (merge log-ctx extra))
                                      ex)))
        state-fn    (or (get state->fn state)
                        (error! "Invalid state"
                                {:biff.fx/available-states (keys state->fn)}
                                nil))
        injected    {:biff.fx/now  (Instant/now)
                     :biff.fx/seed (.nextLong (Random.))}
        state-input (merge ctx input injected)
        result      (try
                      (state-fn state-input)
                      (catch Exception e
                        (error! "State function threw an exception" injected e)))
        _           (biff.core/validate {::state-fn-result result})
        results     (if (sequential? result) result [result])]
    (reduce
     (fn [output result]
       (let [effect-keys (filterv (fn [k]
                                    (let [v (get result k)]
                                      (and (vector? v)
                                           (contains? handlers (first v)))))
                                  (keys result))
             output      (merge output
                                (apply dissoc result effect-keys))]
         (into output
               (map (fn [k]
                      (let [[handler-key & args] (get result k)
                            handler              (get handlers handler-key)]
                        [k
                         (try
                           (apply handler (merge ctx output) args)
                           (catch Exception e
                             (error! "Handler function threw an exception"
                                     {:biff.fx/output       output
                                      :biff.fx/handler-args args}
                                     e)))])))
               effect-keys)))
     {}
     results)))

(defn machine [machine-name & {:as state->fn}]
  (biff.core/validate {::state->fn state->fn})
  (fn run
    ([ctx state]
     ((or (get state->fn state)
          (throw (ex-info "Invalid state"
                          {:biff.fx/state            state
                           :biff.fx/machine-name     machine-name
                           :biff.fx/available-states (keys state->fn)})))
      ctx))
    ([ctx]
     (let [handlers (merge default-fx-handlers
                           (:biff.fx/handlers ctx)
                           (when-some [get-handlers (:biff.fx/get-handlers ctx)]
                             (get-handlers)))
           _        (biff.core/validate {:biff.fx/handlers handlers})
           opts     {:machine-name machine-name
                     :state->fn    state->fn
                     :handlers     handlers
                     :ctx          ctx}]
       (loop [state :start
              input {}
              trace []]
         (let [output (biff.core/validate
                       (step opts
                             {:state state
                              :input input
                              :trace trace}))]
           (cond
             (:biff.fx/next output)
             (recur (:biff.fx/next output)
                    output
                    (conj trace output))

             (contains? output :biff.fx/return)
             (:biff.fx/return output)

             :else output)))))))

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

(defn uuid [seed]
  (let [rng       (Random. seed)
        msb0      (.nextLong rng)
        lsb0      (.nextLong rng)
        ;; Set version to 4
        msb       (-> msb0
                      (bit-and 0xffffffffffff0fff)
                      (bit-or  0x0000000000004000))
        ;; Set RFC 4122 variant
        lsb       (-> lsb0
                      (bit-and 0x3fffffffffffffff)
                      (bit-or  0x8000000000000000))
        next-seed (.nextLong rng)]
    [(UUID. msb lsb) next-seed]))

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
