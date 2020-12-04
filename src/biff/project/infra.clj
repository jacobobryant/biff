(ns biff.project.infra
  (:require
    [clojure.string :as str]))

(defn host->domain [host]
  (let [parts (str/split (str/trim host) #"\.")]
    (->> parts
      (drop (- (count parts) 2))
      (str/join "."))))

(defn host->subdomain-prefix [host]
  (let [parts (str/split (str/trim host) #"\.")]
    (->> parts
      (take (- (count parts) 2))
      (str/join "."))))

(defn do-domain [{:keys [host]}]
  {:digitalocean_domain
   {:default {:name (host->domain host)}}})

(defn do-a-record [{:keys [host]}]
  {:digitalocean_record
   {:default {:domain (host->domain host)
              :type "A"
              :name (or (not-empty (host->subdomain-prefix host)) "@")
              :value "${digitalocean_droplet.webserver.ipv4_address}"}}})

(defn default-terraform-config [opts]
  {:terraform {:required_providers
               {:digitalocean
                {:source "digitalocean/digitalocean"
                 :version "1.22.2"}}}
   :variable {:digitalocean_api_key {:type "string"
                                     :default ""}
              :deploy_key {:type "string"
                           :default ""}
              :image_id {:type "string"
                         :default ""}
              :ssh_key_fingerprint {:type "string"
                                    :default ""}}
   :provider {:digitalocean
              {:token "${var.digitalocean_api_key}"}}
   :resource [{:digitalocean_droplet
               {:webserver
                {:image "${var.image_id}"
                 :name "biff-webserver"
                 :region "nyc1"
                 :size "s-1vcpu-1gb"
                 :private_networking true
                 :ssh_keys ["${var.ssh_key_fingerprint}"]
                 :connection {:host "${self.ipv4_address}"
                              :user "root"
                              :type "ssh"
                              :timeout "2m"}
                 :provisioner [{:file
                                {:source "../config/main.edn"
                                 :destination "/home/biff/config/main.edn"}}
                               {:file
                                {:content "${var.deploy_key}\n"
                                 :destination "/home/biff/.ssh/id_rsa"}}]}}}
              (do-domain opts)
              (do-a-record opts)]})

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
