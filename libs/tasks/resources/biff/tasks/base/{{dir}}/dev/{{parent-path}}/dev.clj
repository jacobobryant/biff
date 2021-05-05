(ns {{parent-ns}}.dev
  (:require [{{parent-ns}}.views :refer [static-pages]]
            [{{parent-ns}}.test]
            [{{parent-ns}}.core :as core]
            [{{parent-ns}}.dev.css :as css]
            [clojure.stacktrace :as st]
            [biff.dev :as dev]
            [biff.util-tmp :as bu]
            [biff.views :as views]
            [clojure.string :as str]
            [clojure.test :as t]))

(defn tests []
  (t/run-all-tests #"{{parent-ns}}.test.*"))

(defn html []
  (views/export-rum static-pages "target/resources/public"))

(defn css []
  (css/write-css {:output-file "target/resources/public/css/main.css"
                  :paths ["src" "dev"]}))

(defn on-file-change []
  (println "=== tests ===")
  (time (tests))
  (println "\n=== html ===")
  (time (html))
  (println "\n=== css ===")
  (time (css))
  (println "\n=== done ==="))

(defn build [_]
  (let [{:keys [fail error]} (tests)]
    (if (< 0 (+ fail error))
      (System/exit 1)
      (do
        (html)
        (css)))))

(defn start []
  (bu/start-system
    (assoc core/config
           :biff/after-refresh `start
           :biff.hawk/callback `on-file-change
           :biff.hawk/paths ["src" "dev"])
    (into [dev/use-hawk] core/components))
  (println "System started."))

(comment
  ; Start the app:
  (start)

  ; Inspect app state:
  (->> @bu/system keys sort (run! prn))

  ; Stop the app, reload files, restart the app:
  (bu/refresh)
  )
