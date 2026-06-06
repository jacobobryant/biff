(ns com.biffweb.tasks.uberjar
  (:require
   [clojure.tools.build.api :as clj-build]
   [com.biffweb.run :as biff.run]
   [com.biffweb.tasks.util :as util]))

(defn uberjar
  "Compiles the app into an uberjar."
  []
  (let [{:biff.tasks/keys [main-ns generate-assets-fn] :as ctx} (util/read-config)
        class-dir                                               "target/jar/classes"
        basis                                                   (clj-build/create-basis {:project "deps.edn"})
        uber-file                                               "target/jar/app.jar"]
    (println "Cleaning...")
    (clj-build/delete {:path "target"})
    (println "Generating CSS...")
    (biff.run/run-task "css" "--minify")
    (println "Calling" generate-assets-fn "...")
    ((requiring-resolve generate-assets-fn) ctx)
    (println "Compiling...")
    (clj-build/compile-clj {:basis      basis
                            :ns-compile [main-ns]
                            :class-dir  class-dir})
    (println "Building uberjar...")
    (clj-build/copy-dir {:src-dirs   ["resources" "target/resources"]
                         :target-dir class-dir})
    (clj-build/uber {:class-dir class-dir
                     :uber-file uber-file
                     :basis     basis
                     :main      main-ns})
    (println "Done. Uberjar written to" uber-file)
    (println (str "Test with `BIFF_PROFILE=dev java -jar " uber-file "`"))))
