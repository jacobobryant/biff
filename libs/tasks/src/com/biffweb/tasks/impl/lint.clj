(ns com.biffweb.tasks.impl.lint
  (:require [clojure.java.shell :as sh]
            [com.biffweb.stuff.bin :as stuff.bin]
            [com.biffweb.tasks.impl.util :as util]))

(def ^:private supported-platforms
  #{[:linux :amd64]
    [:linux :arm64]
    [:macos :amd64]
    [:macos :arm64]
    [:windows :amd64]})

(defn clj-kondo-url [{:keys [os arch version]}]
  (stuff.bin/check-platform {:supported-platforms supported-platforms
                             :binary              "clj-kondo"
                             :version             version
                             :os                  os
                             :arch                arch})
  (let [os-str   (case os
                   :linux "linux"
                   :macos "macos"
                   :windows "windows")
        arch-str (case arch
                   :amd64 "amd64"
                   :arm64 "aarch64")
        variant  (when (= [os arch] [:linux :amd64]) "-static")
        ext      "zip"]
    (str "https://github.com/clj-kondo/clj-kondo/releases/download/v"
         version "/clj-kondo-" version "-" os-str
         variant "-" arch-str "." ext)))

(defn- get-clj-kondo-version [command]
  (let [{:keys [exit out err]} (sh/sh command "--version")]
    (when (zero? exit)
      (some->> (str out "\n" err)
               (re-find #"clj-kondo\s+v?([^\s]+)")
               second))))

(defn ensure-clj-kondo-binary! [target-version]
  (let [{:keys [os arch]} (stuff.bin/platform-info)
        url               (clj-kondo-url
                           {:version target-version :os os :arch arch})]
    (stuff.bin/ensure-binary
     {:executable-basename "clj-kondo"
      :get-version         get-clj-kondo-version
      :target-version      target-version
      :url                 url})))

(defn lint
  []
  (when-some [paths (not-empty (mapv #(.getPath %) (util/clojure-files)))]
    (let [version (:biff.tasks/clj-kondo-version (util/read-config))
          binary  (ensure-clj-kondo-binary! version)]
      (apply util/shell (concat [binary "--parallel" "--lint"]
                                paths))))
  nil)
