(ns com.biffweb.tasks.impl.uberjar-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.tools.build.api :as clj-build]
            [com.biffweb.run :as biff.run]
            [com.biffweb.tasks.impl.uberjar :as uberjar]
            [com.biffweb.tasks.impl.util :as util]))

(deftest jar-resource-builder-replaces-css-task
  (doseq [builder [nil 'example/build-resources]]
    (let [calls (atom [])]
      (with-redefs [util/read-config
                    (constantly {:biff.tasks/main-ns             'example
                                 :biff.tasks/build-jar-resources builder})

                    util/read-deps-edn     (constantly {:paths []})
                    clj-build/create-basis (constantly ::basis)
                    clj-build/delete       #(swap! calls conj [:delete %])
                    clj-build/compile-clj  #(swap! calls conj [:compile %])
                    clj-build/copy-dir     #(swap! calls conj [:copy %])
                    clj-build/uber         #(swap! calls conj [:uber %])

                    biff.run/run-task
                    (fn [& args] (swap! calls conj [:task args]))

                    clojure.core/requiring-resolve
                    (fn [symbol]
                      (is (= builder symbol))
                      #(swap! calls conj [:build-resources]))]
        (with-out-str (uberjar/uberjar)))
      (is (= (if builder
               [[:build-resources]]
               [[:task ["css" "--minify"]]])
             (filterv #(contains? #{:build-resources :task} (first %))
                      @calls))))))
