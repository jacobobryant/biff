(ns com.biffweb.tasks.impl.add-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb.tasks.impl.add :as add]
            [com.biffweb.tasks.impl.lint :as lint]
            [com.biffweb.tasks.impl.util :as util]))

(deftest add-updates-clj-kondo-for-new-classpath-entries
  (let [calls         (atom [])
        old-classpath (System/getProperty "java.class.path")
        separator     java.io.File/pathSeparator
        new-path      (.getCanonicalPath (java.io.File. "new-dependency.jar"))]
    (with-redefs-fn
      {#'add/latest-maven-coordinate
       (constantly ['example/example {:mvn/version "1"}])

       #'add/add-dependency!
       (fn [& args] (swap! calls conj (into [:add] args)))

       #'util/read-config
       (constantly {:biff.tasks/clj-kondo-version "2"})

       #'util/refreshed-classpath
       (constantly (str old-classpath separator new-path))

       #'lint/ensure-clj-kondo-binary! (constantly "kondo")

       #'util/update-clj-kondo-cache!
       (fn [& args] (swap! calls conj (into [:kondo] args)))}
      #(add/add "example/example"))
    (is (= [[:add 'example/example {:mvn/version "1"}]
            [:kondo "kondo" new-path]]
           @calls))))
