(ns tasks.docs
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.biffweb.config]
            [com.biffweb.tasks :as biff.tasks]))

(defn- docs-library? [dir]
  (let [config (io/file dir "dev/config.edn")]
    (and (.isDirectory dir)
         (.isFile config)
         (str/includes? (slurp config) ":biff.tasks/docs-namespaces"))))

(defn- generate-docs [dir]
  (let [config (aero/read-config (io/file dir "dev/config.edn")
                                 {:biff.aero/env {}})]
    (binding [biff.tasks/*extra-config*
              {:biff.tasks/docs-namespaces (:biff.tasks/docs-namespaces config)
               :biff.tasks/docs-directory  (.getPath (io/file dir "docs/api"))}]
      (biff.tasks/docs))))

(defn docs []
  (doseq [dir (->> (.listFiles (io/file "libs"))
                   (filter docs-library?)
                   (sort-by #(.getPath %)))]
    (generate-docs dir))
  nil)
