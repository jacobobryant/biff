(ns com.biffweb.tasks.nrepl
  (:require [com.biffweb.tasks.util :as util]
            [cider.nrepl :as cider-nrepl]
            [nrepl.server :as nrepl]
            [refactor-nrepl.middleware]))

(def ^:private port-file ".nrepl-port")

(def ^:private middleware
  (conj (mapv requiring-resolve cider-nrepl/cider-middleware)
        #'refactor-nrepl.middleware/wrap-refactor))

(defn- delete-port-file []
  (when (util/exists? port-file)
    (let [deleted? (.delete (java.io.File. port-file))]
      (when-not deleted?
        (throw (ex-info "Failed to delete .nrepl-port" {:path port-file}))))))

(defn nrepl
  "Starts an nREPL server without starting the application."
  []
  (let [{:biff.tasks/keys [nrepl-port]} (util/read-config)
        handler                         (apply nrepl/default-handler middleware)
        server                          (if nrepl-port
                                          (nrepl/start-server :port nrepl-port :handler handler)
                                          (nrepl/start-server :handler handler))
        port                            (:port server)]
    (spit port-file port)
    (println "nREPL server started on port" port)
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(do
                                  (nrepl/stop-server server)
                                  (delete-port-file))))
    @(promise)))
