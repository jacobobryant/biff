(ns {{parent-ns}}.dev
  (:require [{{main-ns}} :refer [config components]]
            [{{parent-ns}}.views :refer [static-pages]]
            [{{parent-ns}}.test]
            [{{parent-ns}}.dev.css :as css]
            [clojure.stacktrace :as st]
            [biff.dev :as dev]
            [biff.util :as bu]
            [biff.rum :as br]
            [clojure.string :as str]
            [clojure.test :as t]
            [nrepl.cmdline :as nrepl-cmd]
            [shadow.cljs.devtools.server :as shadow]))

(defn tests []
  (t/run-all-tests #"{{parent-ns}}.test.*"))

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
        (css)))))

(defn use-shadow-cljs [sys]
  (shadow/start!)
  (update sys :biff/stop conj #(shadow/stop!)))

(defn start []
  (bu/start-system
    (assoc config
           :biff/after-refresh `start
           :biff.hawk/callback `on-file-change
           :biff.hawk/paths ["src" "dev"])
    (into [dev/use-hawk use-shadow-cljs] components)))

(defn -main [& args]
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
