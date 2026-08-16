(ns com.biffweb.tasks.impl.format
  (:refer-clojure :exclude [format])
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [com.biffweb.stuff.bin :as stuff.bin]
            [com.biffweb.tasks.impl.util :as util]))

(def ^:private supported-platforms
  #{[:linux :amd64]
    [:linux :arm64]
    [:macos :amd64]
    [:macos :arm64]
    [:windows :amd64]})

(defn cljfmt-url [{:keys [os arch version]}]
  (stuff.bin/check-platform {:supported-platforms supported-platforms
                             :binary              "cljfmt"
                             :version             version
                             :os                  os
                             :arch                arch})
  (let [os-str     (case os
                     :linux "linux"
                     :macos "darwin"
                     :windows "win")
        arch-str   (case arch
                     :amd64 "amd64"
                     :arm64 "aarch64")
        variant    (when (= [os arch] [:linux :amd64]) "-static")
        ext        (case os
                     (:linux :macos) "tar.gz"
                     :windows "zip")
        asset-name (if (= [os arch] [:macos :amd64])
                     (str "cljfmt-" version "-standalone.jar")
                     (str "cljfmt-" version "-" os-str "-"
                          arch-str variant "." ext))]
    (str "https://github.com/weavejester/cljfmt/releases/download/"
         version "/" asset-name)))

(defn- get-cljfmt-version [command]
  (let [{:keys [exit out err]} (sh/sh command "--version")]
    (when (zero? exit)
      (some->> (str out "\n" err)
               (re-find #"cljfmt\s+v?([^\s]+)")
               second))))

(defn- install-cljfmt-jar! [url]
  (let [jar-path    (io/file stuff.bin/bin-dir "cljfmt.jar")
        script-path (io/file stuff.bin/bin-dir "cljfmt")]
    (io/make-parents jar-path)
    (with-open [in  (io/input-stream url)
                out (io/output-stream jar-path)]
      (io/copy in out))
    (spit script-path
          "#!/bin/sh\nexec java -jar \"$(dirname \"$0\")/cljfmt.jar\" \"$@\"\n")
    (.setExecutable script-path true)
    (.getPath script-path)))

(defn ensure-cljfmt-binary! [target-version]
  (let [{:keys [os arch]} (stuff.bin/platform-info)
        intel-mac?        (= [os arch] [:macos :amd64])
        url               (cljfmt-url {:version target-version
                                       :os      os
                                       :arch    arch})]
    (stuff.bin/ensure-binary
     (merge {:executable-basename "cljfmt"
             :get-version         get-cljfmt-version
             :target-version      target-version}
            (if intel-mac?
              {:install #(install-cljfmt-jar! url)}
              {:url url})))))

(defn format
  []
  (when-some [paths (not-empty (mapv #(.getPath %) (util/clojure-files)))]
    (let [version (:biff.tasks/cljfmt-version (util/read-config))
          binary  (ensure-cljfmt-binary! version)]
      (apply util/shell (concat [binary "fix" "--parallel"] paths))))
  nil)
