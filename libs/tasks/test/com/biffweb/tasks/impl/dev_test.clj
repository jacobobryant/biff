(ns com.biffweb.tasks.impl.dev-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [com.biffweb.tasks.impl.dev :as dev]
            [com.biffweb.tasks.impl.util :as util]))

(deftest restarts-after-creating-missing-classpath-directories
  (let [attributes (make-array java.nio.file.attribute.FileAttribute 0)
        root       (.toFile (java.nio.file.Files/createTempDirectory
                             "biff-dev-test" attributes))
        path       (.getPath (io/file root "target/resources"))
        calls      (atom [])]
    (with-redefs [util/all-deps-paths (constantly [path])
                  util/shell          (fn [& args]
                                        (swap! calls conj (vec args)))]
      (dev/dev))
    (is (.isDirectory (io/file path)))
    (is (= [["clojure" "-M:run" "dev"]] @calls))))
