(ns com.biffweb.tasks.publish
  "Helpers for publishing a library to Clojars.

  Release signing is optional, but Clojars recommends it. This task supports
  two signing flows:

  1. `:biff.tasks/gpg-passphrase`
     Use this when the signing key is available locally and you want the task
     to call GPG directly with the passphrase. Create or import a secret key
     into your GPG keyring first, for example with `gpg --full-generate-key`
     to make a new key or `gpg --import` to load an existing one. Then set
     `:biff.tasks/gpg-passphrase` to the key's passphrase, usually via
     `#biff/secret`, e.g. `#biff/secret GPG_PASSPHRASE`.

  2. `:biff.tasks/gpg-sign-key-id`
     Use this when GPG should choose a specific secret key from your keyring
     and obtain the passphrase through normal GPG mechanisms such as
     `gpg-agent`, pinentry, or a hardware token. Create or import the key, run
     `gpg --list-secret-keys --keyid-format LONG` to find its key id, then set
     `:biff.tasks/gpg-sign-key-id` to that value, e.g.
     `#biff/env GPG_SIGN_KEY_ID`.

  If both settings are present, `:biff.tasks/gpg-sign-key-id` takes precedence."
  (:require [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.build.api :as build]
            [com.biffweb.core :as biff.core]
            [com.biffweb.tasks.util :as util]
            [deps-deploy.deps-deploy :as deps-deploy]
            [deps-deploy.gpg :as gpg]
            [hato.client :as hato]))

(def ^:private clojars-repo-url "https://clojars.org/repo")
(def ^:private target-dir "target")
(def ^:private class-dir "target/classes")

(defn- project-root []
  (System/getProperty "user.dir"))

(defn- project-file [path]
  (.getPath (io/file (project-root) path)))

(defn- read-deps-edn []
  (-> (project-file "deps.edn")
      slurp
      edn/read-string))

(defn- existing-paths []
  (let [{:keys [paths]} (read-deps-edn)]
    (->> paths
         (filter #(-> (io/file (project-root) %) .exists))
         vec)))

(defn- resource-dir? [path]
  (= "resources" (.getName (io/file path))))

(defn- source-dirs [paths]
  (->> paths
       (remove resource-dir?)
       vec))

(defn- resource-dirs [paths]
  (->> paths
       (filter resource-dir?)
       vec))

(defn- release-basis [basis ctx]
  (if-not (:biff.tasks/monorepo ctx)
    basis
    (update basis :libs
            (fn [libs]
              (into {}
                    (map (fn [[lib coord]]
                           [lib (cond-> coord
                                  (and (:local/root coord)
                                       (= (:biff.tasks/group-name ctx) (namespace lib)))
                                  (assoc :mvn/version (:biff.tasks/lib-version ctx)))]))
                    libs)))))

(defn- clojars-artifact-url [ctx]
  (str "https://clojars.org/api/artifacts/"
       (java.net.URLEncoder/encode (:biff.tasks/group-name ctx) java.nio.charset.StandardCharsets/UTF_8)
       "/"
       (java.net.URLEncoder/encode (:biff.tasks/lib-name ctx) java.nio.charset.StandardCharsets/UTF_8)))

(defn- published-version? [ctx]
  (let [url                   (clojars-artifact-url ctx)
        {:keys [body status]} (hato/get url {:as :text :throw-exceptions false})]
    (cond
      (= 404 status)
      false

      (= 200 status)
      (let [{:keys [latest_version recent_versions]} (json/read-str body :key-fn keyword)]
        (or (= (:biff.tasks/lib-version ctx) latest_version)
            (some #(= (:biff.tasks/lib-version ctx) (:version %)) recent_versions)))

      :else
      (throw (ex-info "Failed to query Clojars artifact metadata."
                      {:body   body
                       :status status
                       :url    url})))))

(defn- pom-data [{:keys [comments distribution name url]}]
  [[:licenses
    (cond-> [:license
             [:name name]
             [:url url]
             [:distribution (or distribution "repo")]]
      comments
      (conj [:comments comments]))]])

(defn- build-artifact! [ctx]
  (build/with-project-root (project-root)
    (let [basis      (build/create-basis)
          paths      (existing-paths)
          src-dirs   (source-dirs paths)
          res-dirs   (resource-dirs paths)
          lib        (symbol (:biff.tasks/group-name ctx) (:biff.tasks/lib-name ctx))
          class-path (project-file class-dir)
          jar-file   (project-file (str target-dir "/" (:biff.tasks/lib-name ctx) "-" (:biff.tasks/lib-version ctx) ".jar"))
          pom-file   (build/pom-path {:class-dir class-path :lib lib})]
      (build/delete {:path (project-file target-dir)})
      (when (seq paths)
        (build/copy-dir {:src-dirs paths :target-dir class-path}))
      (build/write-pom {:basis         (release-basis basis ctx)
                        :class-dir     class-path
                        :lib           lib
                        :pom-data      (pom-data (:biff.tasks/pom-license ctx))
                        :resource-dirs res-dirs
                        :scm           (:biff.tasks/pom-scm ctx)
                        :src-dirs      src-dirs
                        :version       (:biff.tasks/lib-version ctx)})
      (build/jar {:class-dir class-path
                  :jar-file  jar-file})
      {:jar-file jar-file
       :pom-file pom-file})))

(defn- versioned-pom-filename [{:keys [artifact-id version]}]
  (str artifact-id "-" version ".pom"))

(defn- signed-artifact-map [coordinates jar-file gpg-passphrase gpg-sign-key-id]
  (cond
    gpg-sign-key-id
    (deps-deploy/all-artifacts true coordinates jar-file gpg-sign-key-id)

    gpg-passphrase
    (let [pom-file   (versioned-pom-filename coordinates)
          signatures [(gpg/sign! (gpg-passphrase) pom-file)
                      (gpg/sign! (gpg-passphrase) jar-file)]]
      (deps-deploy/artifacts (:version coordinates)
                             (into [pom-file jar-file] signatures)))

    :else
    (deps-deploy/artifacts (:version coordinates)
                           [(versioned-pom-filename coordinates) jar-file])))

(defn- deploy! [{:biff.tasks/keys [clojars-secret
                                   clojars-username
                                   gpg-passphrase
                                   gpg-sign-key-id]}
                {:keys [jar-file pom-file]}]
  (let [pom-content   (slurp pom-file)
        coordinates   (deps-deploy/coordinates-from-pom pom-file)
        versioned-pom (versioned-pom-filename coordinates)
        repository    {"clojars" {:password (clojars-secret)
                                  :url      clojars-repo-url
                                  :username clojars-username}}]
    (spit versioned-pom pom-content)
    (try
      (deps-deploy/deploy*
       {:artifact-map   (signed-artifact-map coordinates
                                             jar-file
                                             gpg-passphrase
                                             gpg-sign-key-id)
        :coordinates    coordinates
        :installer      :remote
        :repository     repository
        :sign-releases? (boolean (or gpg-passphrase
                                     gpg-sign-key-id))})
      (finally
        (doseq [path [versioned-pom
                      (str versioned-pom ".asc")
                      (str jar-file ".asc")]]
          (when (.exists (io/file path))
            (io/delete-file path)))))))

(defn publish
  "Publishes the current project to Clojars."
  []
  (let [config (biff.core/validate
                (util/read-config)
                {:required [:biff.tasks/group-name
                            :biff.tasks/lib-name
                            :biff.tasks/lib-version
                            :biff.tasks/clojars-username
                            :biff.tasks/clojars-secret
                            :biff.tasks/pom-license
                            :biff.tasks/pom-scm]})]
    (if (published-version? config)
      (println "Already published, skipping:"
               (str (:biff.tasks/group-name config) "/" (:biff.tasks/lib-name config))
               (:biff.tasks/lib-version config))
      (deploy! config (build-artifact! config)))))
