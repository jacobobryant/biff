(ns com.biffweb.tasks.impl.uberjar
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.build.api :as clj-build]
   [com.biffweb.run :as biff.run]
   [com.biffweb.tasks.impl.util :as util]))

(defn uberjar []
  (let [{:biff.tasks/keys [build-jar-resources main-ns]} (util/read-config)

        class-dir      "target/jar/classes"
        uber-file      "target/jar/app.jar"
        basis          (clj-build/create-basis {:project "deps.edn"})
        paths          (:paths (util/read-deps-edn))
        resource-paths (filterv #(str/includes? % "resources") paths)]
    ;; clean
    (println "Cleaning...")
    (when (some #{"target/resources"} resource-paths)
      (clj-build/delete {:path "target/resources"})
      (.mkdirs (io/file "target/resources")))
    (clj-build/delete {:path class-dir})

    ;; build
    (println "Compiling...")
    (clj-build/compile-clj {:basis      basis
                            :ns-compile [main-ns]
                            :class-dir  class-dir})
    (if build-jar-resources
      ((requiring-resolve build-jar-resources))
      (do
        (println "Generating CSS...")
        (biff.run/run-task "css" "--minify")))

    ;; package
    (println "Building uberjar...")
    (clj-build/copy-dir {:src-dirs   resource-paths
                         :target-dir class-dir})
    (clj-build/uber {:class-dir class-dir
                     :uber-file uber-file
                     :basis     basis
                     :main      main-ns})
    (println "Done. Uberjar written to" uber-file)
    (println (str "Test with `java -jar " uber-file "`"))))
