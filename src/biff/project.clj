(ns biff.project
  (:require
    [biff.util :as bu]
    [cheshire.core :as cheshire]
    [clojure.java.io :as io]
    [clojure.java.shell :as shell]
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

(defn copy-files [root opts]
  (let [src-file-prefix (.getPath (io/resource root))]
    (doseq [src-file (filter #(.isFile %)
                       (file-seq (io/file (io/resource root))))
            :let [dest-path (-> src-file
                              .getPath
                              (str/replace-first src-file-prefix "")
                              (selmer/render opts))]]
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

(defn tf-config [opts]
  {:terraform
   {:required_providers
    {:digitalocean
     {:source "digitalocean/digitalocean"
      :version "1.22.2"}}}

   :variable
   {:digitalocean_api_key {}
    :github_deploy_key {}
    :image_id {}}

   :provider
   {:digitalocean
    {:token "var.digitalocean_api_key"}}

   :resource
   [{:digitalocean_droplet
     {:webserver
      {:image "var.image_id"
       :name "biff-webserver"
       :region "nyc1"
       :size "s-1vcpu-1gb"
       :private_networking true
       :connection {:host "self.ipv4_address"
                    :user "root"
                    :type "ssh"
                    :timeout "2m"}
       :provisioner [{:file
                      {:source "config/main.edn"
                       :destination "/home/biff/config/main.edn"}}
                     {:file
                      {:content "var.github_deploy_key"
                       :destination "/home/biff.ssh/id_rsa"}}]}}}

    {:digitalocean_record
     {... {:domain "..."
           :type "A"
           :name "..."
           :value "digitalocean_droplet.webserver.ipv4_address"}}}]})

; todo add config for postgres

(defn init-spa [opts]
  (println "Creating a SPA project.")
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
    (copy-files "biff/project/spa/" opts)
    (spit (str dir "/infra/webserver.json")
      (cheshire/generate-string default-packer-config {:pretty true}))
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

(defn update-spa-files [sys]
  ; todo individual files
  (spit "infra/webserver.json"
    (cheshire/generate-string default-packer-config {:pretty true})))

(defn init-mpa [opts]
  nil)

(defn update-mpa-files [sys]
  nil)

(defn dev [_]
  ((requiring-resolve 'nrepl.server/start-server) :port 7800)
  (println "started"))
#_(init-spa
  {:dir "foobar"
   :host "example.com"
   :main-ns 'hey.core})

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
