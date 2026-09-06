(ns com.biffweb.fx.impl.pipeline
  (:require [com.biffweb.fx.impl :as impl]))

(defn- state-key [i]
  (if (zero? i) :start (keyword (str "pipeline-" i))))

(defn- wrap-input [[input]]
  [(if (contains? input ::result)
     (get input ::result)
     (dissoc input :biff.fx/next))])

(defn- wrap-state [states state-count i state-fn]
  (let [last-state? (= i (dec state-count))
        next-state  (get states (inc i))
        state       (get states i)
        wrap-input* (if (zero? i) identity wrap-input)]
    [state
     (fn [ctx & input]
       (let [input  (wrap-input* input)
             result (apply state-fn ctx input)]
         (cond
           last-state?         result
           (not (map? result)) {::result result :biff.fx/next next-state}
           (contains? result :biff.fx/return) result
           :else (assoc result :biff.fx/next next-state))))]))

(defn pipeline [machine-name & args]
  (let [[initial-fx args] (if (and (vector? (first args))
                                   (keyword? (ffirst args)))
                            [(first args) (rest args)]
                            [nil args])
        state-fns         (if (and (= 1 (count args))
                                   (sequential? (first args)))
                            (vec (first args))
                            (vec args))
        states            (mapv state-key (range (count state-fns)))
        state->fn         (into {}
                                (map-indexed
                                 (fn [i state-fn]
                                   (wrap-state states
                                               (count state-fns)
                                               i
                                               state-fn))
                                 state-fns))
        machine           (apply impl/machine
                                 machine-name
                                 (concat (when initial-fx [initial-fx])
                                         [state->fn]))]
    (fn [& args]
      (if (empty? args)
        state-fns
        (apply machine args)))))

(defmacro defpipeline [sym & args]
  (let [machine-name (keyword (str *ns*) (str sym))]
    `(def ~sym (pipeline ~machine-name ~@args))))
