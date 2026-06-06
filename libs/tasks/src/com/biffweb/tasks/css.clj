(ns com.biffweb.tasks.css
  (:require [com.biffweb.tasks.impl.bin :as bin]
            [com.biffweb.tasks.util :as util]))

(defn css
  "Generates the target/resources/public/css/main.css file."
  [& tailwind-args]
  (let [{:biff.tasks/keys [css-output]} (util/read-config)
        {:keys [tailwind-cmd command]}  (bin/tailwind-command)]
    (try
      (apply util/shell (concat command
                                ["-i" "resources/tailwind.css"
                                 "-o" css-output]
                                tailwind-args))
      (catch Exception e
        (if (and (#{137 139} (:exit (ex-data e)))
                 (#{:local-bin :system-bin} tailwind-cmd))
          (binding [*out* *err*]
            (println "It looks like your Tailwind installation is corrupted."
                     "Try deleting it and running this command again:")
            (println)
            (println "  rm" (first command))
            (println))
          (throw e))))))
