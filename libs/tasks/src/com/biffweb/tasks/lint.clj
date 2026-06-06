(ns com.biffweb.tasks.lint
  (:require [clojure.string :as str]
            [com.biffweb.tasks.impl.bin :as bin]
            [com.biffweb.tasks.util :as util]))

(def ^:private clojure-exts #{".clj" ".cljc" ".cljs" ".edn"})

(defn- clojure-file? [path]
  (some #(str/ends-with? path %) clojure-exts))

(defn- lint-paths []
  (->> (util/git-ls-files)
       (filter clojure-file?)
       vec))

(defn lint
  "Lints tracked Clojure and EDN files with clj-kondo."
  []
  (when-some [paths (not-empty (lint-paths))]
    (let [binary (bin/ensure-binary! :clj-kondo (:biff.tasks/clj-kondo-version (util/read-config)))]
      (apply util/shell (concat [binary "--parallel" "--lint"]
                                paths))))
  nil)
