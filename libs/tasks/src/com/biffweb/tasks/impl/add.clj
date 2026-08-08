(ns com.biffweb.tasks.impl.add
  (:require [borkdude.rewrite-edn :as r]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [com.biffweb.tasks.impl.util :as util]))

(defn- maven-coordinate [value]
  (let [coordinate (symbol value)]
    (when-not (qualified-symbol? coordinate)
      (throw (ex-info (str "Expected a Maven coordinate or git URL: " value)
                      {:value value})))
    coordinate))

(defn- latest-maven-coordinate [coordinate]
  (let [outdated-deps (requiring-resolve 'antq.api/outdated-deps)
        deps-edn      (util/read-deps-edn)
        [dependency]  (outdated-deps {coordinate {:mvn/version "0"}}
                                     {:file-path    "deps.edn"
                                      :repositories (:mvn/repos deps-edn)
                                      :no-changes   true})
        version       (:latest-version dependency)]
    (when-not version
      (throw (ex-info (str "Could not find a release for " coordinate)
                      {:coordinate coordinate})))
    [coordinate {:mvn/version version}]))

(defn- url-parts [url]
  (let [uri  (java.net.URI. url)
        host (.getHost uri)
        path (some-> (.getPath uri)
                     (str/replace #"^/+|/+$" "")
                     (str/replace #"\.git$" ""))]
    (when-not (and host (not (str/blank? path)))
      (throw (ex-info (str "Invalid git URL: " url) {:url url})))
    [host (str/split path #"/")]))

(defn- git-coordinate [url]
  (let [[host path] (url-parts url)
        host-prefix (case host
                      "github.com" "io.github"
                      "gitlab.com" "io.gitlab"
                      (str/join "." (reverse (str/split host #"\."))))
        repo        (peek path)
        owners      (pop path)]
    (when (empty? owners)
      (throw (ex-info (str "Git URL must include an owner and repository: " url)
                      {:url url})))
    (symbol (str host-prefix "." (str/join "." owners)) repo)))

(defn- ls-remote [& args]
  (let [{:keys [exit out err]} (apply sh/sh "git" "ls-remote" args)]
    (when-not (zero? exit)
      (throw (ex-info "git ls-remote failed"
                      {:args args :exit exit :err err})))
    (->> out
         str/split-lines
         (keep #(not-empty (str/split % #"\s+" 2))))))

(defn- latest-git-coordinate [url]
  (let [refs       (ls-remote "--tags" "--sort=-version:refname" url)
        peeled     (into {}
                         (keep (fn [[sha ref]]
                                 (when (str/ends-with? ref "^{}")
                                   [(str/replace ref #"\^\{\}$" "") sha])))
                         refs)
        [sha ref]  (first (remove (comp #(str/ends-with? % "^{}") second)
                                  refs))
        tag        (some-> ref (str/replace #"^refs/tags/" ""))
        sha        (or (get peeled ref)
                       sha
                       (some-> (first (ls-remote url "HEAD")) first))
        coordinate (git-coordinate url)]
    (when-not sha
      (throw (ex-info (str "Could not find a revision for " url) {:url url})))
    [coordinate (merge {:git/url url :git/sha sha}
                       (when tag {:git/tag tag}))]))

(defn- add-dependency! [coordinate value]
  (let [path     (str (util/project-root) "/deps.edn")
        contents (slurp path)
        updated  (binding [*print-namespace-maps* false]
                   (r/assoc-in (r/parse-string contents)
                               [:deps coordinate]
                               value))]
    (spit path (str updated))))

(defn add [value]
  (let [[coordinate dependency]
        (if (re-find #"^[a-z][a-z0-9+.-]*://" value)
          (latest-git-coordinate value)
          (latest-maven-coordinate (maven-coordinate value)))]
    (add-dependency! coordinate dependency)
    (println "Added" coordinate dependency)))
