(ns com.biffweb.run
  (:require [clojure.string :as str]))

(def ^:private tasks {})
(def ^:dynamic *testing* false)

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

        lines  (str/split-lines doc)
        indent (some->> (rest lines)
                        (remove (comp empty? str/trim))
                        not-empty
                        (mapv #(count (take-while #{\ } %)))
                        (apply min))
        doc    (str (first lines) "\n"
                    (->> (rest lines)
                         (map #(subs % (min (count %) indent)))
                         (str/join "\n")))]
    (println doc)))

(defn run-task
  "Runs a given task from the tasks passed to `main`.

   When `main` is called, it stores its `tasks` argument globally. This function
   runs one of those tasks.

   Uses the help logic described in `main`."
  [task-name & args]
  (let [{:keys [task help] :or {help :docstring}}
        (get tasks task-name)

        task-fn (some-> task requiring-resolve)]
    (cond
      (nil? task-fn)
      (throw (ex-info (str "Unrecognized task: " task-name)
                      {::task-name task-name}))

      (and (= :docstring help)
           (#{"help" "--help" "-h"} (first args)))
      (print-help-for task-fn)

      :else
      (apply task-fn args))))

(defn ^:no-doc main* [tasks & args]
  (if (contains? #{"help" "--help" "-h" nil} (first args))
    (do
      (print-help tasks)
      0)
    (do
      (alter-var-root #'com.biffweb.run/tasks (constantly tasks))
      (try
        (apply run-task args)
        0
        (catch clojure.lang.ExceptionInfo e
          (if-some [task-name (::task-name (ex-data e))]
            (do
              (binding [*out* *err*]
                (println "Unrecognized task:" task-name))
              1)
            (throw e)))
        (finally
          (when-not *testing*
            (shutdown-agents)))))))

(defn main
  "Runs a given task.

   `tasks` is a map from task names to task options, like:

     \"my-task\" {:task 'com.example.tasks.my-task/my-task
                :doc  \"one line description of the task.\"}

   `args` is a sequence of command line arguments (strings). If the first
   argument is -h / --help / help, or if no arguments are provided, prints the
   available tasks with their :doc values. Otherwise the first argument must be
   a key in `tasks`, and the :task symbol will be resolved.

   If the first remaining argument is -h / --help / help, prints the task
   function's docstring. (This behavior can be disabled by setting `:help
   :invoke` on the task options.) Otherwise, calls the task function and passes
   the remaining arguments to it.

   Then calls System/exit."
  [tasks & args]
  (let [exit-code (apply main* tasks args)]
    (if *testing*
      exit-code
      (System/exit exit-code))))
