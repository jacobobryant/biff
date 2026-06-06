(ns com.biffweb.tasks.deploy
  (:require [com.biffweb.run :as biff.run]
            [com.biffweb.tasks.reload :as reload]
            [com.biffweb.tasks.util :as util]))

(defn- git-push-url [ctx]
  (str "ssh://" (util/ssh-target ctx) (util/remote-repo-path ctx)))

(defn- ensure-remote-repo! [ctx branch]
  (let [repo (util/remote-repo-path ctx)]
    (util/ssh-run-shell
     ctx
     (str "mkdir -p " (util/shell-quote repo) " && "
          "if [ ! -d " (util/shell-quote (str repo "/.git")) " ]; then "
          "git init " (util/shell-quote repo) " && "
          "git -C " (util/shell-quote repo) " checkout -B " (util/shell-quote branch) "; "
          "fi && "
          "git -C " (util/shell-quote repo) " config receive.denyCurrentBranch updateInstead && "
          "if ! git -C " (util/shell-quote repo) " symbolic-ref HEAD >/dev/null 2>&1; then "
          "git -C " (util/shell-quote repo) " checkout -B " (util/shell-quote branch) "; "
          "fi"))))

(defn- push-branch! [ctx branch]
  (util/shell "git" "push" "--force" (git-push-url ctx) (str "HEAD:" branch)))

(defn- checkout-remote-branch! [ctx branch]
  (util/ssh-run-shell
   ctx
   (str "git -C " (util/shell-quote (util/remote-repo-path ctx))
        " checkout -f " (util/shell-quote branch))))

(defn- remote-load-form [repo-path {:keys [load-files]}]
  (pr-str
   `(do
      (doseq [rel-path# '~load-files]
        (load-file (str ~repo-path "/" rel-path#)))
      :ok)))

(defn- soft-deploy! [ctx]
  (let [{:biff.tasks/keys [nrepl-port]} ctx]
    (when-not nrepl-port
      (throw (ex-info ":biff.tasks/nrepl-port must be set for deploy --soft." {})))
    (let [reload-plan (reload/full-reload-plan "." (util/source-paths))]
      (util/ssh-run
       ctx
       "trench"
       "-p"
       (str nrepl-port)
       "-e"
       (remote-load-form (util/remote-repo-path ctx) reload-plan)))))

(defn deploy
  "Pushes code to the server and either restarts it or performs a soft reload."
  [& args]
  (let [soft?  (some #{"--soft"} args)
        ctx    (util/read-config)
        branch (util/current-git-branch)]
    (util/ensure-clean-worktree!)
    (util/with-ssh-agent ctx
      (biff.run/run-task "css" "--minify")
      (ensure-remote-repo! ctx branch)
      (push-branch! ctx branch)
      (checkout-remote-branch! ctx branch)
      (util/push-deploy-files! ctx)
      (if soft?
        (soft-deploy! ctx)
        (biff.run/run-task "prod-restart")))))
