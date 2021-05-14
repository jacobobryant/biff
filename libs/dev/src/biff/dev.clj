(ns biff.dev
  (:require [clojure.string :as str]
            [hawk.core :as hawk]))

(defn use-hawk [{:biff.hawk/keys [callback exts paths]
                 :or {exts [".clj" ".cljs" ".cljc"]
                      paths ["src"]}
                 :as sys}]
  (let [watch (hawk/watch!
                [(merge {:paths paths
                         :handler (fn [{:keys [last-ran]
                                        :or {last-ran 0}} _]
                                    (when (< 500 (- (inst-ms (java.util.Date.)) last-ran))
                                      ((requiring-resolve callback)))
                                    {:last-ran (inst-ms (java.util.Date.))})}
                        (when exts
                          {:filter (fn [_ {:keys [^java.io.File file]}]
                                     (let [path (.getPath file)]
                                       (some #(str/ends-with? path %) exts)))}))])]
    (update sys :biff/stop conj #(hawk/stop! watch))))
