(ns com.biffweb.tasks.impl.publish
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.tools.build.api :as build]
            [com.biffweb.tasks.impl.util :as util]
            [deps-deploy.deps-deploy :as deps-deploy]
            [deps-deploy.gpg :as gpg]
            [hato.client :as hato]))

(def ^:private clojars-repo-url "https://clojars.org/repo")

(def ^:private required-config-keys
  [:biff.tasks/group-name
   :biff.tasks/lib-name
   :biff.tasks/lib-version
   :biff.tasks/clojars-username
   :biff.tasks/clojars-secret
   :biff.tasks/pom-data
   :biff.tasks/pom-scm])

(defn- project-file [project-root path]
  (.getPath (io/file project-root path)))

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

(defn- release-basis
  [basis {:biff.tasks/keys [group-name lib-version monorepo]}]
  (if-not monorepo
    basis
    (update basis :libs
            (fn [libs]
              (into {}
                    (map (fn [[lib coord]]
                           [lib (cond-> coord
                                  (and (:local/root coord)
                                       (= group-name (namespace lib)))
                                  (assoc :mvn/version lib-version))]))
                    libs)))))

(defn- encode [s]
  (java.net.URLEncoder/encode s java.nio.charset.StandardCharsets/UTF_8))

(defn- clojars-artifact-url
  [{:biff.tasks/keys [group-name lib-name]}]
  (str "https://clojars.org/api/artifacts/"
       (encode group-name)
       "/"
       (encode lib-name)))

(defn- published-version?
  [{:biff.tasks/keys [group-name lib-name lib-version]}]
  (let [url                   (clojars-artifact-url
                               {:biff.tasks/group-name group-name
                                :biff.tasks/lib-name   lib-name})
        {:keys [body status]} (hato/get url {:as               :text
                                             :throw-exceptions false})]
    (cond
      (= 404 status)
      false

      (= 200 status)
      (let [{:keys [latest_version recent_versions]}
            (json/read-str body :key-fn keyword)]
        (or (= lib-version latest_version)
            (some #(= lib-version (:version %)) recent_versions)))

      :else
      (throw (ex-info "Failed to query Clojars artifact metadata."
                      {:body   body
                       :status status
                       :url    url})))))

(defn- build-artifact!
  [project-root
   {:biff.tasks/keys [group-name
                      lib-name
                      lib-version
                      monorepo
                      pom-data
                      pom-scm]}]
  (build/with-project-root project-root
    (let [basis      (build/create-basis)
          paths      (util/deps-paths project-root)
          src-dirs   (source-dirs paths)
          res-dirs   (resource-dirs paths)
          lib        (symbol group-name lib-name)
          class-path (project-file project-root "target/classes")
          jar-file   (project-file
                      project-root
                      (str "target/" lib-name "-" lib-version ".jar"))
          pom-file   (build/pom-path {:class-dir class-path :lib lib})]
      (build/delete {:path class-path})
      (build/delete {:path jar-file})
      (when (seq paths)
        (build/copy-dir {:src-dirs paths :target-dir class-path}))
      (build/write-pom {:basis         (release-basis
                                        basis
                                        {:biff.tasks/group-name  group-name
                                         :biff.tasks/lib-version lib-version
                                         :biff.tasks/monorepo    monorepo})
                        :class-dir     class-path
                        :lib           lib
                        :pom-data      pom-data
                        :resource-dirs res-dirs
                        :scm           pom-scm
                        :src-dirs      src-dirs
                        :version       lib-version})
      (build/jar {:class-dir class-path
                  :jar-file  jar-file})
      {:jar-file jar-file
       :pom-file pom-file})))

(defn- versioned-pom-filename [{:keys [artifact-id version]}]
  (str artifact-id "-" version ".pom"))

(defn- run-deploy! [options sign-key-id]
  (if sign-key-id
    (with-redefs [gpg/read-passphrase (constantly nil)]
      (deps-deploy/deploy options))
    (deps-deploy/deploy options)))

(defn- deploy! [{:biff.tasks/keys [clojars-secret
                                   clojars-username
                                   gpg-sign-key-id
                                   gpg-sign-with-passphrase]}
                {:keys [jar-file pom-file]}]
  (let [coordinates   (deps-deploy/coordinates-from-pom pom-file)
        versioned-pom (versioned-pom-filename coordinates)
        repository    {"clojars" {:url      clojars-repo-url
                                  :username clojars-username
                                  :password (force clojars-secret)}}]
    (try
      (run-deploy!
       {:artifact       jar-file
        :installer      :remote
        :pom-file       pom-file
        :repository     repository
        :sign-key-id    gpg-sign-key-id
        :sign-releases? (or (some? gpg-sign-key-id) gpg-sign-with-passphrase)}
       gpg-sign-key-id)
      (finally
        (doseq [path [(str versioned-pom ".asc")
                      (str jar-file ".asc")]]
          (when (.exists (io/file path))
            (io/delete-file path)))))))

(defn publish []
  (let [config       (util/read-config {:required required-config-keys})
        project-root (.getCanonicalFile
                      (io/file (or (:biff.tasks/project-root config)
                                   (util/project-root))))

        {:biff.tasks/keys [group-name lib-name lib-version]} config]
    (if (published-version?
         (select-keys config [:biff.tasks/group-name
                              :biff.tasks/lib-name
                              :biff.tasks/lib-version]))
      (println "Already published, skipping:"
               (str group-name "/" lib-name)
               lib-version)
      (let [artifact (build-artifact!
                      project-root
                      (select-keys config [:biff.tasks/group-name
                                           :biff.tasks/lib-name
                                           :biff.tasks/lib-version
                                           :biff.tasks/monorepo
                                           :biff.tasks/pom-data
                                           :biff.tasks/pom-scm]))]
        (deploy! (select-keys config [:biff.tasks/clojars-secret
                                      :biff.tasks/clojars-username
                                      :biff.tasks/gpg-sign-key-id
                                      :biff.tasks/gpg-sign-with-passphrase])
                 artifact)))))
