(ns com.biffweb.sqlite.impl.litestream-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb.stuff.bin :as bin]
            [com.biffweb.sqlite.impl.defaults :as defaults]
            [com.biffweb.sqlite.impl.litestream :as litestream]))

(def ^:private version (:biff.sqlite/litestream-version defaults/defaults))

(defn- prefixed-urls [prefix paths]
  (into #{} (map #(str prefix %)) paths))

(def ^:private unsupported-urls
  (prefixed-urls
   (str "https://github.com/benbjohnson/litestream/releases/download/v" version "/")
   #{"checksums.txt"
     (str "litestream-" version "-darwin-arm64.tar.gz.sbom.json")
     (str "litestream-" version "-darwin-x86_64.tar.gz.sbom.json")
     (str "litestream-" version "-linux-arm64.deb")
     (str "litestream-" version "-linux-arm64.rpm")
     (str "litestream-" version "-linux-arm64.tar.gz.sbom.json")
     (str "litestream-" version "-linux-armv6.deb")
     (str "litestream-" version "-linux-armv6.rpm")
     (str "litestream-" version "-linux-armv6.tar.gz")
     (str "litestream-" version "-linux-armv6.tar.gz.sbom.json")
     (str "litestream-" version "-linux-armv7.deb")
     (str "litestream-" version "-linux-armv7.rpm")
     (str "litestream-" version "-linux-armv7.tar.gz")
     (str "litestream-" version "-linux-armv7.tar.gz.sbom.json")
     (str "litestream-" version "-linux-x86_64.deb")
     (str "litestream-" version "-linux-x86_64.rpm")
     (str "litestream-" version "-linux-x86_64.tar.gz.sbom.json")
     (str "litestream-" version "-windows-arm64.zip.sbom.json")
     (str "litestream-" version "-windows-x86_64.zip.sbom.json")
     (str "litestream-vfs-v" version "-darwin-amd64.tar.gz")
     (str "litestream-vfs-v" version "-darwin-amd64.tar.gz.sha256")
     (str "litestream-vfs-v" version "-darwin-arm64.tar.gz")
     (str "litestream-vfs-v" version "-darwin-arm64.tar.gz.sha256")
     (str "litestream-vfs-v" version "-linux-amd64.tar.gz")
     (str "litestream-vfs-v" version "-linux-amd64.tar.gz.sha256")
     (str "litestream-vfs-v" version "-linux-arm64.tar.gz")
     (str "litestream-vfs-v" version "-linux-arm64.tar.gz.sha256")}))

(deftest binary-urls-match-release
  (bin/assert-binary-urls
   {:fixture-path       (str "com/biffweb/sqlite/impl/litestream-urls-"
                             version ".edn")
    :github-release-url (str "https://api.github.com/repos/benbjohnson/litestream/releases/tags/v" version)
    :unsupported-urls   unsupported-urls
    :url-fn             litestream/litestream-url
    :version            version}))

(deftest installs-binary-for-current-platform
  (is (some? (litestream/ensure-litestream-binary! {}))))

(deftest config-uses-custom-replica-path
  (let [attrs       (make-array java.nio.file.attribute.FileAttribute 0)
        dir         (java.nio.file.Files/createTempDirectory "litestream-test"
                                                             attrs)
        config-dir  (str dir)
        config-file (.resolve dir "litestream.yml")
        ctx         {:biff.sqlite/db-path           "storage/sqlite/main.db"
                     :biff.sqlite/litestream-bucket "backups"
                     :biff.sqlite/litestream-dir    config-dir}]
    (try
      (#'litestream/write-config! ctx)
      (is (.contains (slurp (str config-file))
                     (str "        path: " config-dir "/main.db\n")))
      (#'litestream/write-config!
       (assoc ctx :biff.sqlite/litestream-replica-path "production/database"))
      (is (.contains (slurp (str config-file))
                     "        path: production/database\n"))
      (finally
        (java.nio.file.Files/deleteIfExists config-file)
        (java.nio.file.Files/deleteIfExists dir)))))
