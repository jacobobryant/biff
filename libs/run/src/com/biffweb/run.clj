(ns com.biffweb.run
  (:require [clojure.string :as str]))

(def tasks {})

(defn- print-help [tasks]
  (let [col-width (apply max (mapv count (keys tasks)))]
    (println "Available commands:")
    (println)
    (doseq [[task-name {:keys [doc]}] (sort-by key tasks)]
      (printf (str "  %-" col-width "s%s\n")
              task-name
              (str " - " doc)))
    (flush)))

(defn- print-help-for [task-fn]
  (let [{:keys [doc] :or {doc ""}} (meta task-fn)
        lines                      (str/split-lines doc)
        indent                     (some->> lines
                                            rest
                                            (remove (comp empty? str/trim))
                                            not-empty
                                            (mapv #(count (take-while #{\ } %)))
                                            (apply min))
        doc                        (str (first lines) "\n"
                                        (->> (rest lines)
                                             (map #(subs % (min (count %) indent)))
                                             (str/join "\n")))]
    (println doc)))

(defn run-task [task-name & args]
  (let [task-fn (some-> (get-in tasks [task-name :task])
                        requiring-resolve)]
    (cond
      (nil? task-fn)
      (throw (ex-info (str "Unrecognized task: " task-name)
                      {::task-name task-name}))

      (#{"help" "--help" "-h"} (first args))
      (print-help-for task-fn)

      :else
      (apply task-fn args))))

(defn main* [tasks & args]
  (when (contains? #{"help" "--help" "-h" nil} (first args))
    (print-help tasks)
    (System/exit 0))
  (alter-var-root #'tasks (constantly tasks))
  (try
    (apply run-task args)
    (catch clojure.lang.ExceptionInfo e
      (if-some [task-name (::task-name (ex-data e))]
        (binding [*out* *err*]
          (println "Unrecognized task:" task-name)
          (shutdown-agents)
          (System/exit 1))
        (throw e)))
    (finally
      (shutdown-agents)))
  (System/exit 0))
