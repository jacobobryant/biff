(ns biff.dev
  "Helper functions for use in development."
  (:require [clojure.string :as str]
            [hawk.core :as hawk]))

(defn use-hawk
  "A Biff component for Hawk. See https://github.com/wkf/hawk.

  callback:      Deprecated, use callback-fn. A fully-qualified symbol for a
                 zero-argument function to call whenever a file is saved. The
                 function is called no more than once every 500 milliseconds.
  callback-fn:   A single-argument function to call whenever a file is saved.
                 Receives the system map as a parameter. The function is called
                 no more than once every 500 milliseconds.
  call-on-start: If true, triggers a callback immediately.
  paths:         A collection of root directories to monitor for file changes.
  exts:          If exts is provided, files that don't end in one of the
                 extensions will be ignored."
  [{:biff.hawk/keys [callback callback-fn exts paths call-on-start]
    :or {exts [".clj" ".cljs" ".cljc"]
         paths ["src"]}
    :as sys}]
  (when call-on-start
    (callback-fn sys))
  (let [watch (hawk/watch!
                [(merge {:paths paths
                         :handler (fn [{:keys [last-ran]
                                        :or {last-ran 0}} _]
                                    (when (< 500 (- (inst-ms (java.util.Date.)) last-ran))
                                      (if callback-fn
                                        (callback-fn sys)
                                        ((requiring-resolve callback))))
                                    {:last-ran (inst-ms (java.util.Date.))})}
                        (when exts
                          {:filter (fn [_ {:keys [^java.io.File file]}]
                                     (let [path (.getPath file)]
                                       (some #(str/ends-with? path %) exts)))}))])]
    (update sys :biff/stop conj #(hawk/stop! watch))))
