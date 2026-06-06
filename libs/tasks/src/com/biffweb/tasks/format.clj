(ns com.biffweb.tasks.format
  (:refer-clojure :exclude [format])
  (:require [cljfmt.config :as cljfmt-config]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.biffweb.tasks.impl.bin :as bin]
            [com.biffweb.tasks.util :as util]))

(def ^:private clojure-exts #{".clj" ".cljc" ".cljs" ".edn"})

(defn- project-root []
  (io/file (System/getProperty "user.dir")))

(defn- clojure-file? [path]
  (some #(str/ends-with? path %) clojure-exts))

(defn format-paths []
  (let [root (project-root)]
    (->> (util/git-ls-files)
         (filter clojure-file?)
         (mapv #(.getPath (io/file root %))))))

(defn- project-config []
  (if-some [config-file (cljfmt-config/find-config-file "" {})]
    (-> config-file
        cljfmt-config/read-config
        cljfmt-config/convert-legacy-keys)
    {}))

(defn- merged-config []
  (merge
   {:align-form-columns? true
    :align-map-columns?  true
    :extra-aligned-forms {'let #{0}}}
   (project-config)))

(defn- write-config-file! [config]
  (let [file (.toFile (java.nio.file.Files/createTempFile "biff-cljfmt" ".edn"
                                                          (make-array java.nio.file.attribute.FileAttribute 0)))]
    (spit file (pr-str config))
    file))

(defn format
  "Formats the repo's Clojure and EDN files with cljfmt."
  []
  (when-some [paths (not-empty (format-paths))]
    (let [binary      (bin/ensure-binary! :cljfmt (:biff.tasks/cljfmt-version (util/read-config)))
          config-file (write-config-file! (merged-config))]
      (try
        (apply util/shell (concat [binary "fix" "--config" (.getPath config-file)]
                                  paths))
        (finally
          (io/delete-file config-file true)))))
  nil)
