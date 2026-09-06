(ns com.biffweb.tasks.impl.test
  (:refer-clojure :exclude [test]))

(defn test [& args]
  ;; Use -main* because -main calls System/exit
  (let [exit-code (apply (requiring-resolve 'kaocha.runner/-main*) args)]
    (when-not (zero? exit-code)
      (throw (ex-info "Tests failed" {:exit exit-code})))))
