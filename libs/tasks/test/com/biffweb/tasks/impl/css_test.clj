(ns com.biffweb.tasks.impl.css-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [com.biffweb.stuff.bin :as bin]
            [com.biffweb.tasks.impl.css :as css]
            [com.biffweb.tasks.impl.util :as tasks.util]))

(def ^:private version
  (:biff.tasks/tailwind-version tasks.util/config-defaults))

(defn- prefixed-urls [prefix paths]
  (into #{} (map #(str prefix %)) paths))

(def ^:private unsupported-urls
  (prefixed-urls
   (str "https://github.com/tailwindlabs/tailwindcss/releases/download/v" version "/")
   #{"sha256sums.txt"
     "tailwindcss-linux-arm64-musl"
     "tailwindcss-linux-x64-musl"}))

(deftest binary-urls-match-release
  (bin/assert-binary-urls
   {:fixture-path       (str "com/biffweb/tasks/impl/tailwindcss-urls-"
                             version ".edn")
    :github-release-url (str "https://api.github.com/repos/tailwindlabs/tailwindcss/releases/tags/v" version)
    :unsupported-urls   unsupported-urls
    :url-fn             css/tailwindcss-url
    :version            version}))

(deftest installs-binary-for-current-platform
  (is (some? (css/ensure-tailwind-binary! version))))

(deftest skips-compilation-when-input-is-missing
  (let [shell-called? (atom false)]
    (with-redefs [io/file          (constantly (proxy [java.io.File] ["missing"]
                                                 (exists [] false)))
                  tasks.util/shell (fn [& _] (reset! shell-called? true))]
      (is (= (str "resources/tailwind.css doesn't exist, "
                  "skipping CSS compilation\n")
             (with-out-str (css/css "--watch"))))
      (is (false? @shell-called?)))))
