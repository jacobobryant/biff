(ns com.biffweb.tasks.impl.nrepl
  (:require [com.biffweb.tasks.impl.util :as util]
            [nrepl.cmdline :as nrepl.cmdline]))

(defn nrepl
  [& args]
  (let [{:biff.tasks/keys [nrepl-port]} (util/read-config)

        args (if (= "--" (first args))
               (rest args)
               (concat (when nrepl-port
                         ["--port" (str nrepl-port)])
                       ["--middleware" "[cider.nrepl/cider-middleware]"]
                       args))]
    (apply nrepl.cmdline/-main args)))
