(ns com.biffweb.tasks.impl.dev
  (:require [clojure.stacktrace :as st]
            [com.biffweb.run :as biff.run]
            [com.biffweb.tasks.impl.reload :as reload]
            [com.biffweb.tasks.impl.util :as util]
            [nextjournal.beholder :as beholder])
  (:import [java.util.concurrent ArrayBlockingQueue Executors TimeUnit]))

(defn- run-with-printed-exceptions [f]
  (try
    (f)
    (catch Exception e
      (when-not (#{130 143} (:exit (ex-data e)))
        (binding [*err* *out*]
          (st/print-stack-trace e))))))

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
                   (run-with-printed-exceptions
                    on-change)))))
    {:executor executor
     :watcher  watcher}))

(defn dev []
  (if-not (util/ensure-paths!)
    (util/shell-inherit "clojure" "-M:run" "dev")
    (let [_
          (biff.run/run-task "init")

          {:biff.tasks/keys [main-ns]} (util/read-config)]
      (future
        (run-with-printed-exceptions
         #(biff.run/run-task "css" "--watch=always")))
      (start-file-watcher! {:directories (util/src-paths)
                            :on-change   #'reload/refresh})
      ((requiring-resolve (symbol (str main-ns) "-main"))))))
