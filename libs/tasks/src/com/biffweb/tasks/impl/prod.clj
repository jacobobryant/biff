(ns com.biffweb.tasks.impl.prod
  (:require [clojure.java.io :as io]
            [com.biffweb.tasks.impl.util :as util]))

(defn- root-ssh-target [{:biff.tasks/keys [domain]}]
  (str "root@" domain))

(defn- ssh-root-run-shell [ctx command]
  (util/shell "ssh" (root-ssh-target ctx)
              (str "sh -lc " (util/shell-quote command))))

(defn- resource->temp-file [resource-path]
  (let [tmp (java.io.File/createTempFile "biff-server-setup-" ".sh")]
    (with-open [in  (io/input-stream (io/resource resource-path))
                out (io/output-stream tmp)]
      (io/copy in out))
    (.setExecutable tmp true)
    tmp))

(defn prod-setup [& args]
  (when-some [invalid-arg (first (remove #{"--copy-only"} args))]
    (throw (ex-info (str "Invalid argument: " invalid-arg) {})))
  (util/ensure-prod-alias!)
  (let [{:biff.tasks/keys [deployment-name domain] :as ctx}
        (util/read-config '{:required [domain]
                            :select   [deployment-name skip-ssh-agent]})

        local-script  (resource->temp-file "com/biffweb/tasks/server-setup.sh")
        remote-script (str "/tmp/" deployment-name "-server-setup.sh")
        copy-only     (boolean (some #{"--copy-only"} args))
        copy-script   (fn []
                        (util/shell
                         "scp"
                         (.getPath local-script)
                         (str (root-ssh-target ctx) ":" remote-script)))
        run-script    (str "bash " (util/shell-quote remote-script) " "
                           (util/shell-quote deployment-name) " "
                           (util/shell-quote domain))]
    (try
      (if copy-only
        (do
          (copy-script)
          (println "Script copied to server. Finish setup with "
                   "`ssh root@" domain " " run-script "`"))
        (util/with-ssh-agent ctx
          (copy-script)
          (ssh-root-run-shell
           ctx
           (str run-script " && rm -f " (util/shell-quote remote-script)))))
      (finally
        (io/delete-file local-script true)))))

(defn prod-restart
  []
  (let [{:biff.tasks/keys [deployment-name] :as ctx}
        (util/read-config '{:select [deployment-name domain]})]
    (util/ssh-run-shell
     ctx
     (str "sudo systemctl reset-failed "
          (util/shell-quote (str deployment-name ".service"))
          " || true; "
          "sudo systemctl restart " (util/shell-quote deployment-name)))))

(defn prod-nrepl
  []
  (let [{:biff.tasks/keys [nrepl-port] :as ctx}
        (util/read-config '{:required [nrepl-port]
                            :select   [deployment-name domain]})]
    (println "Connect to nREPL port" nrepl-port)
    (spit ".nrepl-port" nrepl-port)
    (try
      (util/shell "ssh" "-NL"
                  (str nrepl-port ":localhost:" nrepl-port)
                  (util/ssh-target ctx))
      (finally
        (io/delete-file ".nrepl-port" true)))))

(defn prod-logs
  ([] (prod-logs "300"))
  ([n-lines]
   (let [{:biff.tasks/keys [deployment-name] :as ctx}
         (util/read-config {:select '[deployment-name domain]})]
     (util/ssh-run ctx "journalctl" "-u" deployment-name
                   "-n" n-lines "-f" "--no-pager"))))
