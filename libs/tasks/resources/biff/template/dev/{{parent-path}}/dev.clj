(ns {{parent-ns}}.dev
  (:require [{{main-ns}} :as core]
            [{{parent-ns}}.env :refer [use-env]]
            [{{parent-ns}}.views :refer [static-pages]]
            [{{parent-ns}}.test]
            [{{parent-ns}}.dev.css :as css]
            [biff.dev :as dev]
            [biff.rum :as br]
            [biff.util :as bu]
            [clojure.stacktrace :as st]
            [clojure.string :as str]
            [clojure.test :as t]
            [hf.depstar.uberjar :as uber]
            [nrepl.cmdline :as nrepl-cmd]
            [shadow.cljs.devtools.api :as shadow-api]
            [shadow.cljs.devtools.server :as shadow-server]))

(defn tests []
  (t/run-all-tests #"{{parent-ns}}.*test.clj"))

(defn html []
  (br/export-rum static-pages "target/resources/public"))

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
        (css)
        (shadow-api/release :app)
        (if (:success (uber/build-jar
                        {:aot true
                         :main-class '{{main-ns}}
                         :jar "target/app.jar"}))
          (System/exit 0)
          (System/exit 2))))))

(defn use-shadow-cljs [sys]
  (shadow-server/start!)
  (shadow-api/watch :app)
  (update sys :biff/stop conj #(shadow-server/stop!)))

(defn start []
  (bu/start-system
    (assoc core/config
           :biff/after-refresh `start
           :biff.hawk/callback `on-file-change
           :biff.hawk/paths ["src" "dev"])
    (into [dev/use-hawk use-shadow-cljs] core/components)))

(defn -main [& args]
  (on-file-change)
  (start)
  (apply nrepl-cmd/-main args))

(comment
  ; Start the app (not needed by default since this is called by -main, but
  ; useful if you prefer to start clj + nrepl from your editor):
  (start)

  ; Inspect app state:
  (->> @bu/system keys sort (run! prn))

  ; Stop the app, reload files, restart the app:
  (bu/refresh)
  ; If you have problems with your editor capturing stdout/stderr, you can use
  ; this instead (I've needed this with Conjure):
  (bu/fix-print (bu/refresh))
  )
