(ns com.biffweb.tasks.impl.dev
  (:require [clojure.java.io :as io]
            [clojure.stacktrace :as st]
            [com.biffweb.run :as biff.run]
            [com.biffweb.tasks.impl.reload :as reload]
            [com.biffweb.tasks.impl.util :as util]
            [nextjournal.beholder :as beholder])
  (:import [clojure.lang DynamicClassLoader RT]
           [java.util.concurrent ArrayBlockingQueue Executors TimeUnit]))

(defn- start-file-watcher!
  [{:keys [directories on-change debounce-ms]
    :or   {debounce-ms 500}}]
  (let [queue    (ArrayBlockingQueue. 1)
        executor (Executors/newSingleThreadExecutor)
        watcher  (apply beholder/watch
                        (fn [_event]
                          (.offer queue true))
                        directories)]
    (.submit executor
             ^Runnable
             (reify Runnable
               (run [_]
                 (while true
                   (.take queue)
                   (while (.poll queue debounce-ms TimeUnit/MILLISECONDS))
                   (on-change)))))
    {:executor executor
     :watcher  watcher}))

(defn- add-classpath! [path]
  (let [loader (RT/baseLoader)]
    (when-not (instance? DynamicClassLoader loader)
      (throw (ex-info "The base classloader cannot add paths"
                      {:class (class loader)
                       :path  path})))
    (.addURL ^DynamicClassLoader loader
             (-> path io/file .toURI .toURL))
    (.setContextClassLoader (Thread/currentThread) loader)))

(defn dev []
  (let [{:biff.tasks/keys [main-ns]} (util/read-config)
        paths                        (util/all-deps-paths)]
    (doseq [path  paths
            :when (not (.exists (io/file path)))]
      (io/make-parents (io/file path "_"))
      (add-classpath! path))
    (future
      (try
        (biff.run/run-task "css" "--watch")
        (catch Exception e
          (binding [*err* *out*]
            (st/print-stack-trace e)))))
    (start-file-watcher! {:directories paths
                          :on-change   #'reload/refresh})
    ((requiring-resolve (symbol (str main-ns) "-main")))))
