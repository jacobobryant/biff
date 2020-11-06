(ns biff.project.terraform.digitalocean
  (:require
    [clojure.string :as str]))
; I am greatly tempted to rename this ns to biff.project.doterraform

(def terraform
  {:required_providers
   {:digitalocean
    {:source "digitalocean/digitalocean"
     :version "1.22.2"}}})

(def variables
  {:digitalocean_api_key {:type "string"
                          :default ""}
   :deploy_key {:type "string"
                :default ""}
   :image_id {:type "string"
              :default ""}
   :ssh_key_fingerprint {:type "string"
                         :default ""}})

(def providers
  {:digitalocean
   {:token "${var.digitalocean_api_key}"}})

(def webserver
  {:digitalocean_droplet
   {:webserver
    {:image "${var.image_id}"
     :name "biff-webserver"
     :region "nyc1"
     :size "s-1vcpu-1gb"
     :private_networking true
     :ssh_keys ["${var.ssh_key_fingerprint}"]
     :connection {:host "self.ipv4_address"
                  :user "root"
                  :type "ssh"
                  :timeout "2m"}
     :provisioner [{:file
                    {:source "../config/main.edn"
                     :destination "/home/biff/config/main.edn"}}
                   {:file
                    {:content "${var.deploy_key}"
                     :destination "/home/biff/.ssh/id_rsa"}}]}}})

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

(defn domain [{:keys [host]}]
  {:digitalocean_domain
   {:default {:name (host->domain host)}}})

(defn a-record [{:keys [host]}]
  {:digitalocean_record
   {:default {:domain (host->domain host)
              :type "A"
              :name (or (not-empty (host->subdomain-prefix host)) "@")
              :value "${digitalocean_droplet.webserver.ipv4_address}"}}})

(defn system [opts]
  {:terraform terraform
   :variable variables
   :provider providers
   :resource [webserver
              (domain opts)
              (a-record opts)]})
