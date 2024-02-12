(ns com.biffweb.task-runner
  (:require [com.biffweb.task-runner.lazy.clojure.string :as str]))

(def tasks {})

(defn- print-help [tasks]
  (let [col-width (apply max (mapv count (keys tasks)))]
    (println "Available commands:")
    (println)
    (doseq [[task-name task-var] (sort-by key tasks)
            :let [doc (some-> (:doc (meta task-var))
                              str/split-lines
                              first)]]
      (printf (str "  %-" col-width "s%s\n")
              task-name
              (if doc
                (str " - " doc)
                "")))))

(defn- print-help-for [task-fn]
  (let [{:keys [doc] :or {doc ""}} (meta task-fn)
        lines (str/split-lines doc)
        indent (some->> lines
                        rest
                        (remove (comp empty? str/trim))
                        not-empty
                        (mapv #(count (take-while #{\ } %)))
                        (apply min))
        doc (str (first lines) "\n"
                 (->> (rest lines)
                      (map #(subs % (min (count %) indent)))
                      (str/join "\n")))]
    (println doc)))

(defn run-task [task-name & args]
  (let [task-fn (get tasks task-name)]
    (cond
      (nil? task-fn)
      (binding [*out* *err*]
        (println (str "Unrecognized task: " task-name))
        (System/exit 1))

      (#{"help" "--help" "-h"} (first args))
      (print-help-for task-fn)

      :else
      (apply task-fn args))))

(defn -main
  ([tasks-sym]
   (-main tasks-sym "--help"))
  ([tasks-sym task-name & args]
   (let [tasks @(requiring-resolve (symbol tasks-sym))]
     (if (contains? #{"help" "--help" "-h" nil} task-name)
       (print-help tasks)
       (do
         (alter-var-root #'tasks (constantly tasks))
         (apply run-task task-name args)))
     (shutdown-agents))))
