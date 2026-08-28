(ns com.biffweb.tasks.impl.css
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [com.biffweb.stuff.bin :as stuff.bin]
            [com.biffweb.tasks.impl.util :as util]))

(def ^:private supported-platforms
  #{[:linux :amd64]
    [:linux :arm64]
    [:macos :amd64]
    [:macos :arm64]
    [:windows :amd64]})

(defn tailwindcss-url [{:keys [os arch version]}]
  (stuff.bin/check-platform {:supported-platforms supported-platforms
                             :binary              "tailwindcss"
                             :version             version
                             :os                  os
                             :arch                arch})
  (let [os-str     (case os
                     :linux "linux"
                     :macos "macos"
                     :windows "windows")
        arch-str   (case arch
                     :amd64 "x64"
                     :arm64 "arm64")
        ext        (case os
                     (:linux :macos) ""
                     :windows ".exe")
        asset-name (str os-str "-" arch-str ext)]
    (str "https://github.com/tailwindlabs/tailwindcss/releases/download/v"
         version "/tailwindcss-" asset-name)))

(defn- get-tailwindcss-version [command]
  (let [{:keys [exit out err]} (sh/sh command "--version")]
    (when (zero? exit)
      (some->> (str out "\n" err)
               (re-find #"tailwindcss\s+v?([^\s]+)")
               second))))

(defn ensure-tailwind-binary! [target-version]
  (let [{:keys [os arch]} (stuff.bin/platform-info)
        url               (tailwindcss-url {:version target-version
                                            :os      os
                                            :arch    arch})]
    (stuff.bin/ensure-binary
     {:executable-basename "tailwindcss"
      :get-version         get-tailwindcss-version
      :target-version      target-version
      :url                 url})))

(defn css [& tailwind-args]
  (let [{:biff.tasks/keys [css-input-path css-output-path tailwind-version]}
        (util/read-config)]
    (if-not (.exists (io/file css-input-path))
      (println (str css-input-path " doesn't exist, skipping CSS compilation"))
      (let [command (ensure-tailwind-binary! tailwind-version)
            shell   (if (some #(str/starts-with? % "--watch") tailwind-args)
                      util/shell-inherit
                      util/shell)]
        (apply shell
               (concat [command]
                       ["-i" css-input-path "-o" css-output-path]
                       tailwind-args))))))
