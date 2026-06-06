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
      (binding [*out* *err*]
        (println (str "Unrecognized task: " task-name))
        (System/exit 1))

      (#{"help" "--help" "-h"} (first args))
      (print-help-for task-fn)

      :else
      (apply task-fn args))))

(defn main* [tasks task-name & args]
  (if (contains? #{"help" "--help" "-h" nil} task-name)
    (print-help tasks)
    (do
      (alter-var-root #'tasks (constantly tasks))
      (apply run-task task-name args)))
  (System/exit 0))

(defn -main [tasks-str & args]
  (let [tasks (->> (str/split tasks-str #",")
                   (mapv (fn [task-str]
                           @(requiring-resolve (symbol task-str))))
                   (apply merge))]
    (apply main* tasks args)))
