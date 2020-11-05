(ns biff.project
  (:require
    [biff.project.terraform.digitalocean :as do]
    [biff.util :as bu]
    [cheshire.core :as cheshire]
    [clojure.java.io :as io]
    [clojure.java.shell :as shell]
    [clojure.set :as set]
    [clojure.string :as str]
    [selmer.parser :as selmer]))

(defn prompt [{:keys [msg default] :as opts}]
  (print msg)
  (flush)
  (or (not-empty (read-line))
    default
    (recur opts)))

(defn get-opts [opts key-infos]
  (reduce (fn [opts {:keys [k msg f] :as key-info}]
            (cond
              (contains? opts k) opts
              f (do
                  (println msg)
                  (assoc opts k (f)))
              :default (assoc opts k (prompt key-info))))
    opts
    key-infos))

(defn latest-biff-sha []
  (-> (bu/sh "git" "ls-remote" "https://github.com/jacobobryant/biff.git" "HEAD")
    (str/split #"\s+")
    first))

(defn add-derived [{:keys [main-ns] :as opts}]
  (let [parent-ns (str/replace (str main-ns) #"(\.core)$" "")
        parent-path (str/replace parent-ns "." "/")
        main-ns-path (str/replace (str main-ns) "." "/")]
    (assoc opts
      :parent-ns parent-ns
      :parent-path parent-path
      :main-ns-path main-ns-path)))

(defn copy-files [root {:keys [files] :as opts}]
  (let [src-file-prefix (.getPath (io/resource root))]
    (doseq [src-file (filter #(.isFile %)
                       (file-seq (io/file (io/resource root))))
            :let [src-file-postfix (-> src-file
                                     .getPath
                                     (str/replace-first src-file-prefix ""))
                  dest-path (selmer/render src-file-postfix opts)]
            :when (or (nil? files) (contains? files src-file-postfix))]
      (io/make-parents dest-path)
      (spit dest-path (selmer/render (slurp src-file) opts)))))

(def default-packer-config
  {:builders [{:type "digitalocean"
               :api_token "{{user `digitalocean_api_key`}}"
               :image "ubuntu-20-04-x64"
               :region "nyc1"
               :size "s-1vcpu-1gb"
               :private_networking true
               :snapshot_name "biff-webserver"
               :ssh_username "root"
               :monitoring true}]
   :provisioners [{:type "file"
                   :source "./provisioners"
                   :destination "/tmp/"}
                  {:type "shell"
                   :script "run-provisioners.sh"}]
   :variables {:digitalocean_api_key "{{env `DIGITALOCEAN_API_KEY`}}"}
   :sensitive-variables ["digitalocean_api_key"]})

(defn init [{:keys [template-path] :as opts}]
  (let [{:keys [dir] :as opts}
        (-> opts
          (get-opts [{:k :sha
                      :msg "Fetching latest Biff version..."
                      :f latest-biff-sha}
                     {:k :dir
                      :msg "Enter name for project directory: "}
                     {:k :main-ns
                      :msg "Enter main namespace (e.g. example.core): "}
                     {:k :host
                      :msg (str "Enter the domain you plan to use in production (e.g. example.com),\n"
                             "or leave blank to choose later: ")
                      :default "example.com"}])
          (update :sha str)
          (update :dir str)
          (update :main-ns symbol)
          add-derived)]
    (copy-files "biff/project/base/" opts)
    (copy-files template-path opts)
    (spit (str dir "/infra/webserver.json")
      (cheshire/generate-string default-packer-config {:pretty true}))
    (spit (str dir "/infra/system.tf.json")
      (cheshire/generate-string (do/system opts) {:pretty true}))
    (bu/sh "chmod" "+x" (str dir "/task"))
    (doseq [f (file-seq (io/file dir "config"))]
      (bu/sh "chmod" (if (.isFile f) "600" "700") (.getPath f)))
    (println)
    (println "Your project is ready. Run the following commands to get started:")
    (println)
    (println "  cd" dir)
    (println "  git init")
    (println "  ./task init")
    (println "  ./task dev")
    (println)
    (println "Run `./task help` for more info.")))

(defn init-spa [opts]
  (println "Creating a SPA project.")
  (init (assoc opts :spa true :template-path "biff/project/spa/")))

(defn init-mpa [opts]
  (println "Creating an MPA project.")
  (init (assoc opts :mpa true :template-path "biff/project/mpa/")))

(defn update-files [{:keys [template-path] :as sys}]
  (let [opts (assoc sys :host (get-in sys [:biff/unmerged-config :prod :biff/host]))]
    (copy-files "biff/project/base/{{dir}}/"
      (assoc opts
        :files #{"all-tasks/10-biff"
                 "infra/provisioners/10-wait"
                 "infra/provisioners/20-dependencies"
                 "infra/provisioners/30-non-root-user"
                 "infra/provisioners/40-app"
                 "infra/provisioners/50-systemd"
                 "infra/provisioners/60-nginx"
                 "infra/provisioners/70-firewall"
                 "infra/run-provisioners.sh"}))
    (spit "infra/webserver.json"
      (cheshire/generate-string default-packer-config {:pretty true}))
    (spit "infra/system.tf.json"
      (cheshire/generate-string (do/system opts) {:pretty true}))))

(defn update-spa-files [sys]
  (update-files (assoc sys :spa true)))

(defn update-mpa-files [sys]
  (update-files (assoc sys :mpa true)))

(defn -main []
  (println "Creating a new Biff project. Available project types:")
  (println)
  (println "  1. SPA (single-page application). Includes ClojureScript, React, and")
  (println "     Biff's subscribable queries. Good for highly interactive applications.")
  (println)
  (println "  2. MPA (multi-page application). Uses server-side rendering instead of")
  (println "     React etc. Good for simpler applications.")
  (println)
  (print "Choose a project type ([spa]/mpa): ")
  (flush)
  (if (str/starts-with? (str/lower-case (read-line)) "m")
    (init-mpa {})
    (init-spa {}))
  (System/exit 0))
