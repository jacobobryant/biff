(ns com.biffweb.tasks.prod
  (:require [clojure.java.io :as io]
            [com.biffweb.tasks.util :as util]))

(defn- resource->temp-file [resource-path]
  (let [tmp (java.io.File/createTempFile "biff-server-setup-" ".sh")]
    (with-open [in  (io/input-stream (io/resource resource-path))
                out (io/output-stream tmp)]
      (io/copy in out))
    (.setExecutable tmp true)
    tmp))

(defn install
  "Uploads and runs the canonical production setup script as root."
  []
  (let [{:biff.tasks/keys [deployment-name domain] :as ctx} (util/read-config)
        local-script                                        (resource->temp-file "com/biffweb/tasks/server-setup.sh")
        remote-script                                       (str "/tmp/" deployment-name "-server-setup.sh")]
    (try
      (util/with-ssh-agent ctx
        (util/shell "scp" (.getPath local-script) (str (util/root-ssh-target ctx) ":" remote-script))
        (util/ssh-root-run-shell
         ctx
         (str "chmod +x " (util/shell-quote remote-script)
              " && "
              (util/shell-quote remote-script) " "
              (util/shell-quote deployment-name) " "
              (util/shell-quote domain)
              " && rm -f " (util/shell-quote remote-script))))
      (finally
        (io/delete-file local-script true)))))

(defn restart
  "Restarts the production systemd service."
  []
  (let [{:biff.tasks/keys [deployment-name] :as ctx} (util/read-config)]
    (util/ssh-run-shell
     ctx
     (str "sudo systemctl reset-failed " (util/shell-quote (str deployment-name ".service"))
          " || true; "
          "sudo systemctl restart " (util/shell-quote deployment-name)))))

(defn nrepl
  "Opens an SSH tunnel to the production nREPL port."
  []
  (let [{:biff.tasks/keys [nrepl-port] :as ctx} (util/read-config)]
    (when-not nrepl-port
      (throw (ex-info ":biff.tasks/nrepl-port must be set for prod-nrepl." {})))
    (println "Connect to nREPL port" nrepl-port)
    (spit ".nrepl-port" nrepl-port)
    (util/shell "ssh" "-NL" (str nrepl-port ":localhost:" nrepl-port) (util/ssh-target ctx))))

(defn logs
  "Tails the production service logs."
  []
  (let [{:biff.tasks/keys [deployment-name] :as ctx} (util/read-config)]
    (util/ssh-run ctx "journalctl" "-u" deployment-name "-n" "300" "-f")))
