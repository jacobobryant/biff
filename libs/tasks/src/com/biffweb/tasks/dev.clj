(ns com.biffweb.tasks.dev
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [com.biffweb.run :as biff.run]
            [com.biffweb.tasks.format :as tasks-format]
            [com.biffweb.tasks.generate :as generate]
            [com.biffweb.tasks.install-tailwind :as install-tailwind]
            [com.biffweb.tasks.lint :as tasks-lint]
            [com.biffweb.tasks.reload :as reload]
            [com.biffweb.tasks.test :as tasks-test]
            [com.biffweb.tasks.util :as util]
            [nextjournal.beholder :as beholder])
  (:import [java.util Timer TimerTask]))

(def ^:private status-file ".biff-dev-status.edn")
(def ^:private watcher-ignore-ms 100)

;; https://gist.github.com/oliyh/0c1da9beab43766ae2a6abc9507e732a
(defn- debounce
  ([f] (debounce f 1000))
  ([f timeout]
   (let [timer (Timer.)
         task  (atom nil)]
     (with-meta
       (fn [& args]
         (when-let [t ^TimerTask @task]
           (.cancel t))
         (let [new-task (proxy [TimerTask] []
                          (run []
                            (apply f args)
                            (reset! task nil)
                            (.purge timer)))]
           (reset! task new-task)
           (.schedule timer new-task timeout)))
       {:task-atom task}))))

(defn- install-js-deps-cmd []
  (if (util/exists? "bun.lockb")
    "bun install"
    "npm install"))

(defn- normalize-status [status]
  (-> status
      pr-str
      (#(edn/read-string {:default (fn [_tag value]
                                     value)}
                         %))))

(defn- status->string [status]
  (-> status
      normalize-status
      pr-str))

(defn- persisted-status [{:keys [started-at finished-at] :as status}]
  (cond-> status
    started-at
    (assoc :started-at (str started-at))

    finished-at
    (assoc :finished-at (str finished-at))))

(defn- started-at-ms [started-at]
  (cond
    (inst? started-at)
    (inst-ms started-at)

    (instance? java.time.Instant started-at)
    (.toEpochMilli ^java.time.Instant started-at)

    (string? started-at)
    (.toEpochMilli (java.time.Instant/parse started-at))

    :else
    nil))

(defn- read-status []
  (when (util/exists? status-file)
    (edn/read-string
     {:default (fn [_tag value] value)}
     (slurp status-file))))

(let [lock (Object.)]
  (defn- write-status! [status]
    (locking lock
      (let [old-started-at (started-at-ms (:started-at (read-status)))
            status         (persisted-status status)
            started-at     (started-at-ms (:started-at status))]
        (when (or (nil? old-started-at)
                  (and started-at
                       (<= old-started-at started-at)))
          (println)
          (pprint status)
          (println)
          (spit status-file (status->string status)))))))

(defn- watcher-state []
  (atom {:ignore-events-until 0
         :processing?         false
         :rerun-requested?    false}))

(defn- ignoring-events? [state]
  (< (System/currentTimeMillis)
     (:ignore-events-until @state)))

(defn- begin-processing? [state]
  (let [[old _new] (swap-vals! state
                               #(if (:processing? %)
                                  %
                                  (assoc % :processing? true)))]
    (not (:processing? old))))

(defn- format-result [state]
  (swap! state assoc :ignore-events-until Long/MAX_VALUE)
  (try
    (tasks-format/format)
    {:status :ok}
    (catch Throwable t
      {:status :failure
       :result t})
    (finally
      (swap! state assoc :ignore-events-until (+ (System/currentTimeMillis)
                                                 watcher-ignore-ms)))))

(defn- eval-result []
  (let [result (time (reload/refresh! (util/deps-paths)))]
    (if (instance? Throwable result)
      {:status :failure
       :result result}
      {:status :ok
       :result result})))

(defn- lint-result []
  (try
    (tasks-lint/lint)
    {:status :ok}
    (catch Throwable t
      {:status :failure
       :result t})))

(defn- test-result [eval]
  (if (= :failure (:status eval))
    {:status :skipped
     :reason :eval-failure}
    (let [{:keys [fail error] :as result} (time (tasks-test/run-tests))]
      (if (zero? (+ fail error))
        {:status :ok
         :result result}
        {:status :failure
         :result result}))))

(defn- overall-status [{:keys [format eval lint test]}]
  (cond
    (= :failure (:status format)) :format-failure
    (= :failure (:status eval))   :eval-failure
    (= :failure (:status lint))   :lint-failure
    (= :failure (:status test))   :test-failure
    :else                         :ok))

(defn- process-changes-once! [state]
  (let [started-at (java.time.Instant/now)]
    (write-status! {:status     :running
                    :started-at started-at
                    :format     {:status :pending}
                    :eval       {:status :pending}
                    :lint       {:status :pending}
                    :test       {:status :pending}})
    (let [format (format-result state)
          eval   (eval-result)
          lint   (lint-result)
          test   (test-result eval)]
      (write-status! {:status      (overall-status {:format format
                                                    :eval   eval
                                                    :lint   lint
                                                    :test   test})
                      :started-at  started-at
                      :finished-at (java.time.Instant/now)
                      :format      format
                      :eval        eval
                      :lint        lint
                      :test        test}))))

(defn- process-changes! [state]
  (when (begin-processing? state)
    (try
      (loop []
        (swap! state assoc :rerun-requested? false)
        (process-changes-once! state)
        (when (:rerun-requested? @state)
          (recur)))
      (finally
        (swap! state assoc :processing? false)))))

(defn- handle-watch-event! [state flush! _event]
  (when-not (ignoring-events? state)
    (if (:processing? @state)
      (swap! state assoc :rerun-requested? true)
      (flush!))))

(defn- start-watchers! [state]
  (let [flush! (debounce #(process-changes! state) 500)]
    (apply beholder/watch
           (fn [event]
             (handle-watch-event! state flush! event))
           (util/deps-paths))))

(defn dev
  "Starts the app locally and keeps CSS, formatting, linting, tests, and file evaluation up to date."
  [& args]
  (let [minify-css? (some #{"--minify-css"} args)]
    (if-not (util/exists? "target/resources")
      (do
        (io/make-parents "target/resources/_")
        (apply util/shell (concat ["clj" "-M:run" "dev"]
                                  (when minify-css?
                                    ["--minify-css"]))))
      (let [{:biff.tasks/keys [main-ns]} (util/read-config)
            state                        (watcher-state)]
        (generate/ensure-config-files)
        (when (util/exists? "package.json")
          (util/shell (install-js-deps-cmd)))
        (install-tailwind/ensure-tailwind-installed)
        (util/future
          (apply biff.run/run-task
                 (concat ["css" "--watch"]
                         (when minify-css?
                           ["--minify"]))))
        (start-watchers! state)
        ((requiring-resolve (symbol (str main-ns) "-main")))))))
