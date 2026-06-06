(ns com.biffweb.fx.impl
  "Implementation details for com.biffweb.fx. Do not use directly."
  (:require [clojure.walk :as walk]
            [com.biffweb.core :as biff.core])
  (:import [java.security SecureRandom]
           [java.time Instant]
           [java.util Random]))

(defn truncate-str [s n]
  (if (<= (count s) n) s (str (subs s 0 (dec n)) "…")))

(defn truncate [data]
  (walk/postwalk
   (fn [data] (if (string? data) (truncate-str data 500) data))
   data))

(def default-fx-handlers
  {:biff.fx/http
   (fn [_ctx request-or-requests]
     (let [hato-request (requiring-resolve 'hato.client/request)
           http*        (fn [request]
                          (try
                            (-> (hato-request request)
                                (assoc :url (:url request))
                                (dissoc :http-client))
                            (catch Exception e
                              (if (get request :throw-exceptions true)
                                (throw e)
                                {:url       (:url request)
                                 :exception e}))))]
       (if (map? request-or-requests)
         (http* request-or-requests)
         (mapv http* request-or-requests))))

   :biff.fx/slurp
   (fn [_ctx & args]
     (apply slurp args))

   :biff.fx/spit
   (fn [_ctx & args]
     (apply spit args))

   :biff.fx/sleep
   (fn [_ctx sleep-ms]
     (Thread/sleep (long sleep-ms)))

   :biff.fx/temp-dir
   (fn [_ctx & {:keys [prefix]}]
     (let [dir (java.nio.file.Files/createTempDirectory
                (or prefix "biff")
                (into-array java.nio.file.attribute.FileAttribute []))]
       (.toFile dir)))

   :biff.fx/secure-random-int
   (fn [_ctx n]
     (.nextInt (SecureRandom.) n))})

(defn step [{:keys [state->transition-fn ctx state trace handlers]}]
  (let [handled-fx-keys (set (keys handlers))
        last-results    (->> (some-> trace peek :biff.fx/results)
                             (mapv :biff.fx/fx-output)
                             (filterv not-empty))
        ctx             (assoc ctx
                               :biff.fx/now (Instant/now)
                               :biff.fx/seed (.nextLong (Random.))
                               :biff.fx/results last-results)
        t-fn            (or (get state->transition-fn state)
                            (throw (ex-info "Invalid state" {:state state})))
        result          (t-fn ctx)
        results         (if (map? result) [result] result)
        _               (biff.core/validate results)
        results         (mapv
                         (fn [m]
                           (let [effect-entry? (fn [[_ v]]
                                                 (and (vector? v)
                                                      (seq v)
                                                      (keyword? (first v))
                                                      (contains? handled-fx-keys (first v))))
                                 effect-keys   (set (map key (filter effect-entry? m)))
                                 state-output  (apply dissoc m effect-keys)
                                 fx-input      (select-keys m effect-keys)
                                 ctx           (merge ctx state-output)
                                 fx-output
                                 (into {}
                                       (map (fn [[k v]]
                                              (let [handler-key  (first v)
                                                    handler-args (rest v)
                                                    handler-fn   (get handlers handler-key)]
                                                [k (try
                                                     (apply handler-fn ctx handler-args)
                                                     (catch Exception e
                                                       (throw
                                                        (ex-info
                                                         "Exception while running biff.fx effect"
                                                         (truncate {:effect handler-key
                                                                    :key    k
                                                                    :input  (vec handler-args)})
                                                         e))))])))
                                       fx-input)]
                             {:biff.fx/state-output state-output
                              :biff.fx/fx-input     fx-input
                              :biff.fx/fx-output    fx-output}))
                         results)
        trace           (conj trace {:biff.fx/state   state
                                     :biff.fx/results results})
        {:biff.fx/keys [state-output fx-output fx-input]}
        (apply merge-with merge results)
        next-state      (:biff.fx/next state-output)]
    {:next-state   next-state
     :ctx          (merge ctx fx-output state-output
                          {:biff.fx/trace trace :biff.fx/fx-input fx-input})
     :trace        trace
     :state-output state-output
     :fx-output    fx-output}))

(def all-methods
  [:get :post :put :delete :head :options :trace :patch :connect])

(defn safe-for-url? [s]
  (boolean (re-matches #"[a-zA-Z0-9-_.+!*]+" s)))

(defn autogen-endpoint [ns* sym]
  (let [href (str "/_biff/api/" ns* "/" sym)]
    (doseq [segment [ns* sym]]
      (assert (safe-for-url? (str segment))
              (str "URL segment would contain invalid characters: " segment)))
    href))

(defn route*
  [uri route-name machine-fn & {:as state->transition-fn}]
  (let [machine* (machine-fn route-name state->transition-fn)]
    [uri
     (into {:name route-name}
           (comp (filter state->transition-fn)
                 (map (fn [method]
                        [method machine*])))
           all-methods)]))

(defn wrap-result
  [f]
  (fn [ctx]
    (f ctx (:biff.fx/result ctx))))

(defn wrap-hiccup
  [f]
  (fn [& args]
    (let [result (apply f args)]
      (if (and (vector? result) (keyword? (first result)))
        {:body result}
        result))))

(defn wrap-methods
  [params wrapper-fn]
  (reduce (fn [m method]
            (if (contains? m method)
              (update m method wrapper-fn)
              m))
          params
          all-methods))
