(ns com.biffweb.tasks.impl.agent
  (:require [clojure.test :as t]
            [com.biffweb.run :as biff.run]
            [com.biffweb.tasks.impl.reload :as reload])
  (:import [java.io StringWriter]))

(defn- exception-data [e]
  (let [data (ex-data e)]
    {:class   (.getName (class e))
     :message (ex-message e)
     :data    (if (= :babashka.process/error (:type data))
                (select-keys data [:exit :type])
                data)}))

(defn agent-refresh []
  (let [out    (StringWriter.)
        err    (StringWriter.)
        result (binding [*out*        out
                         *err*        err
                         t/*test-out* out]
                 (try
                   (let [refresh-result (reload/refresh)]
                     (when (instance? Throwable refresh-result)
                       (throw refresh-result)))
                   (biff.run/run-task "lint")
                   (biff.run/run-task "test"
                                      "--reporter" "kaocha.report/result"
                                      "--no-color")
                   {:status :ok}
                   (catch Exception e
                     {:status    :error
                      :exception (exception-data e)})))]
    (assoc result
           :out (str out)
           :err (str err))))
