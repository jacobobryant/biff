(ns com.biffweb.tasks.impl.format-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb.stuff.bin :as bin]
            [com.biffweb.tasks.impl.format :as format]
            [com.biffweb.tasks.impl.util :as tasks.util]))

(def ^:private version (:biff.tasks/cljfmt-version tasks.util/config-defaults))

(defn- prefixed-urls [prefix paths]
  (into #{} (map #(str prefix %)) paths))

(def ^:private unsupported-urls
  (prefixed-urls
   (str "https://github.com/weavejester/cljfmt/releases/download/"
        version "/cljfmt-" version "-")
   #{"darwin-aarch64.tar.gz.sha256"
     "linux-aarch64.tar.gz.sha256"
     "linux-amd64-static.tar.gz.sha256"
     "linux-amd64.tar.gz"
     "linux-amd64.tar.gz.sha256"
     "standalone.jar.sha256"
     "win-amd64.zip.sha256"}))

(deftest binary-urls-match-release
  (bin/assert-binary-urls
   {:fixture-path       (str "com/biffweb/tasks/impl/cljfmt-urls-"
                             version ".edn")
    :github-release-url (str "https://api.github.com/repos/weavejester/cljfmt/releases/tags/" version)
    :unsupported-urls   unsupported-urls
    :url-fn             format/cljfmt-url
    :version            version}))

(deftest installs-binary-for-current-platform
  (is (some? (format/ensure-cljfmt-binary! version))))

(deftest fixes-pair-spacing
  (is (= "(let [a\n      1\n\n      b\n      2]\n  [a b])\n"
         (format/fix-pair-spacing
          "(let [a\n      1\n      b\n      2]\n  [a b])\n")))
  (is (= "{:a 1\r\n\r\n :b\r\n 2}\r\n"
         (format/fix-pair-spacing
          "{:a 1\r\n :b\r\n 2}\r\n")))
  (is (= "(cond\n  a\n  1\n\n  b\n  2)\n"
         (format/fix-pair-spacing
          "(cond\n  a\n  1\n  b\n  2)\n"))))
