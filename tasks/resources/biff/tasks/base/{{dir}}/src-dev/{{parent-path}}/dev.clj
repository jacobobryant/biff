(ns {{parent-ns}}.dev
  (:require [{{parent-ns}}.test]
            [clojure.test :as t]
            [girouette.processor :as gir]))

(defn run-tests []
  (t/run-all-tests #"{{parent-ns}}.test.*"))

(defn css [opts]
  (gir/process
    (merge '{:css {:output-file "target/resources/public/css/main.css"}
             :garden-fn {{parent-ns}}.dev.css/class-name->garden
             :apply-classes {{parent-ns}}.dev.css/apply-classes}
           opts)))

(comment
  ; Compile css in background:
  (.start (Thread. #(css {:watch? true})))

  ; Start the app:
  ((requiring-resolve '{{main-ns}}/-main))

  ; Inspect app state:
  (->> @biff.core/system keys sort (run! prn))

  ; Stop the app, reload files, restart the app:
  (biff.core/refresh)

  ; Run tests:
  (run-tests)
  )
