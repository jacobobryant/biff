(ns com.biffweb.tasks.impl.lint-test
  (:require [clojure.test :refer [deftest]]
            [com.biffweb.stuff.bin :as bin]
            [com.biffweb.tasks.impl.lint :as lint]
            [com.biffweb.tasks.impl.util :as tasks.util]))

(def ^:private version
  (:biff.tasks/clj-kondo-version tasks.util/config-defaults))

(defn- prefixed-urls [prefix paths]
  (into #{} (map #(str prefix %)) paths))

(def ^:private unsupported-urls
  (prefixed-urls
   (str "https://github.com/clj-kondo/clj-kondo/releases/download/v" version "/")
   #{(str "clj-kondo-" version "-linux-aarch64.zip.sha256")
     (str "clj-kondo-" version "-linux-amd64.zip")
     (str "clj-kondo-" version "-linux-amd64.zip.sha256")
     (str "clj-kondo-" version "-linux-static-amd64.zip.sha256")
     (str "clj-kondo-" version "-macos-aarch64.zip.sha256")
     (str "clj-kondo-" version "-macos-amd64.zip.sha256")
     (str "clj-kondo-" version "-standalone.jar")
     (str "clj-kondo-" version "-standalone.jar.sha256")
     (str "clj-kondo-" version "-windows-amd64.zip.sha256")
     (str "clj-kondo-lsp-server-" version "-standalone.jar")
     (str "clj-kondo-lsp-server-" version "-standalone.jar.sha256")}))

(deftest binary-urls-match-release
  (bin/assert-binary-urls
   {:fixture-path       (str "com/biffweb/tasks/impl/clj-kondo-urls-"
                             version ".edn")
    :github-release-url (str "https://api.github.com/repos/clj-kondo/clj-kondo/releases/tags/v" version)
    :unsupported-urls   unsupported-urls
    :url-fn             lint/clj-kondo-url
    :version            version}))
