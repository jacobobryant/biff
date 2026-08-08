(ns com.biffweb.tasks.impl.deploy
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [com.biffweb.run :as biff.run]
            [com.biffweb.tasks.impl.util :as util]
            [babashka.fs :as fs]))

(defn- remote-repo-path [{:biff.tasks/keys [deployment-name]}]
  (str "/home/" deployment-name "/repo"))

(defn- deploy-file-spec [file]
  (if (string? file)
    {:src file :dest file}
    file))

(defn- push-deploy-files! [{:biff.tasks/keys [deploy-untracked-files] :as ctx}]
  (let [files (->> deploy-untracked-files
                   (mapv deploy-file-spec)
                   (filterv #(.exists (io/file (:src %)))))]
    (when-some [dirs (->> files
                          (keep (comp not-empty fs/parent :dest))
                          distinct
                          vec
                          not-empty)]
      (util/ssh-run-shell
       ctx
       (str "mkdir -p "
            (str/join " "
                      (map #(util/shell-quote
                             (str (remote-repo-path ctx) "/" %))
                           dirs)))))
    (doseq [{:keys [src dest]} files]
      (util/shell "scp" src (str (util/ssh-target ctx)
                                 ":"
                                 (remote-repo-path ctx) "/" dest)))))

(defn- current-git-branch []
  (let [{:keys [exit out]} (sh/sh "git" "branch" "--show-current")
        branch             (some-> out str/trim not-empty)]
    (when-not (zero? exit)
      (throw (ex-info "Failed to read the current git branch" {:exit exit})))
    (when-not branch
      (throw (ex-info (str "Deploy requires a branch checkout; "
                           "HEAD is detached.") {})))
    branch))

(defn- ensure-clean-worktree! []
  (let [{:keys [exit out]} (sh/sh "git" "status" "--porcelain")]
    (when-not (zero? exit)
      (throw (ex-info "Failed to inspect git status" {:exit exit})))
    (when (not-empty (str/trim out))
      (throw (ex-info "Deploy requires a clean worktree." {:status out})))))

(defn- git-push-url [ctx]
  (str "ssh://" (util/ssh-target ctx) (remote-repo-path ctx)))

(defn- ensure-remote-repo! [ctx branch]
  (let [repo        (remote-repo-path ctx)
        quoted-repo (util/shell-quote repo)]
    (util/ssh-run-shell
     ctx
     (str "mkdir -p " quoted-repo " && "
          "if [ ! -d " (util/shell-quote (str repo "/.git")) " ]; then "
          "  git init " quoted-repo " && "
          "  git -C " quoted-repo " checkout -B " (util/shell-quote branch) "; "
          "fi && "
          ;; let clients push to this repository
          "git -C " quoted-repo
          " config receive.denyCurrentBranch updateInstead && "
          ;; if HEAD is detached, check out the given branch
          "if ! git -C " quoted-repo " symbolic-ref HEAD >/dev/null 2>&1; then "
          "  git -C " quoted-repo " checkout -B " (util/shell-quote branch) "; "
          "fi"))))

(defn- push-branch! [ctx branch]
  (util/shell "git" "push" "--force" (git-push-url ctx) (str "HEAD:" branch)))

(defn- checkout-remote-branch! [ctx branch]
  (util/ssh-run-shell
   ctx
   (str "git -C " (util/shell-quote (remote-repo-path ctx))
        " checkout -f " (util/shell-quote branch))))

(defn- remote-load-form [repo-path {:keys [load-files]}]
  (pr-str
   `(do
      (doseq [rel-path# '~load-files]
        (load-file (str ~repo-path "/" rel-path#)))
      :ok)))

(defn- soft-deploy! [{:biff.tasks/keys [nrepl-port] :as ctx}]
  (let [reload-plan
        ((requiring-resolve 'com.biffweb.tasks.impl.reload/full-reload-plan)
         "." (util/deps-paths))]
    (util/ssh-run
     ctx
     "trench"
     "-p" (str nrepl-port)
     "-e" (remote-load-form (remote-repo-path ctx) reload-plan))))

(defn deploy [& args]
  (when-some [invalid-arg (first (remove #{"--soft"} args))]
    (throw (ex-info (str "Invalid argument: " invalid-arg) {})))
  (let [soft   (some #{"--soft"} args)
        ctx    (util/read-config {:required
                                  (into '[domain] (when soft '[nrepl-port]))

                                  :select '[skip-ssh-agent
                                            deployment-name
                                            deploy-untracked-files
                                            nrepl-port]})
        branch (current-git-branch)]
    (ensure-clean-worktree!)
    (util/with-ssh-agent ctx
      (biff.run/run-task "css" "--minify")
      (ensure-remote-repo! ctx branch)
      (push-branch! ctx branch)
      (checkout-remote-branch! ctx branch)
      (push-deploy-files! ctx)
      (if soft
        (soft-deploy! ctx)
        (biff.run/run-task "prod-restart")))))
