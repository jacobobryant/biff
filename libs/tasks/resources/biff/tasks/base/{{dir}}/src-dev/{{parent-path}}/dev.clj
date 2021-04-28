(ns {{parent-ns}}.dev
  (:require [{{parent-ns}}.static :refer [pages]]
            [{{parent-ns}}.test]
            [biff.components :as bc]
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

(defn build [_]
  (let [{:keys [fail error]} (run-tests)]
    (if (< 0 (+ fail error))
      (System/exit 1)
      (do
        (css nil)
        (bc/export-rum pages "target/resources/public")))))

(comment
  ; Compile resources:
  (.start (Thread. #(css {:watch? true})))
  (bc/export-rum pages "target/resources/public")

  ; Start the app:
  ((requiring-resolve '{{main-ns}}/-main))

  ; Inspect app state:
  (->> @biff.util-tmp/system keys sort (run! prn))

  ; Stop the app, reload files, restart the app:
  (biff.util-tmp/refresh)

  ; Run tests:
  (run-tests)
  )
