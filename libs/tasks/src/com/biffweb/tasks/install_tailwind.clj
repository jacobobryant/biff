(ns com.biffweb.tasks.install-tailwind
  (:require
   [com.biffweb.tasks.config :as config]
   [com.biffweb.tasks.impl.bin :as bin]))

(defn install-tailwind
  "Downloads a Tailwind binary to bin/tailwindcss."
  [& [file]]
  (let [{:biff.tasks/keys [tailwind-build tailwind-version]} (config/read)]
    (bin/install-binary! :tailwindcss
                         (cond-> {:version (or tailwind-version
                                               (bin/default-version :tailwindcss))}
                           (or file tailwind-build)
                           (assoc :asset-name (or file
                                                  (str "tailwindcss-" tailwind-build)))))))

(defn ensure-tailwind-installed []
  (bin/tailwind-command)
  nil)
