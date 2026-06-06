(ns com.biffweb.tasks.impl.bin
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [com.biffweb.tasks.util :as util])
  (:import [java.util UUID]
           [java.util.zip ZipInputStream]
           [java.net HttpURLConnection SocketTimeoutException URI]))

(def ^:private default-versions
  {:clj-kondo   "2026.05.25"
   :cljfmt      "0.16.4"
   :tailwindcss "4.3.0"})

(def ^:private download-connect-timeout-ms 30000)
(def ^:private download-read-timeout-ms 300000)

(defn default-version [tool]
  (get default-versions tool))

(defn- os-key []
  (let [os-name (str/lower-case (System/getProperty "os.name"))]
    (cond
      (str/includes? os-name "windows") :windows
      (str/includes? os-name "linux") :linux
      (str/includes? os-name "mac") :macos
      :else (throw (ex-info "Unsupported operating system"
                            {:os-name os-name})))))

(defn- arch-key []
  (let [arch (str/lower-case (System/getProperty "os.arch"))]
    (case arch
      ("amd64" "x86_64") :amd64
      ("aarch64" "arm64") :arm64
      (throw (ex-info "Unsupported architecture"
                      {:arch arch})))))

(defn- executable-name [tool]
  (let [name (name tool)]
    (if (util/windows?)
      (str name ".exe")
      name)))

(defn local-bin-path [tool]
  (str "bin/" (executable-name tool)))

(defn local-bin-installed? [tool]
  (util/exists? (local-bin-path tool)))

(defn system-bin-path [tool]
  (some-> (util/which (executable-name tool))
          str))

(defn- ensure-local-executable! [tool]
  (when (local-bin-installed? tool)
    (.setExecutable (io/file (local-bin-path tool)) true)))

(defn- descriptor [tool]
  (case tool
    :clj-kondo
    {:repo            "clj-kondo/clj-kondo"
     :archive-format  :zip
     :version-pattern #"clj-kondo\s+v?([^\s]+)"
     :release-tag     #(str "v" %)
     :asset-name
     (fn [version _opts]
       (let [os   (case (os-key)
                    :linux "linux"
                    :macos "macos"
                    :windows "windows")
             arch (case [(os-key) (arch-key)]
                    [:linux :amd64] "amd64"
                    [:linux :arm64] "aarch64"
                    [:macos :amd64] "amd64"
                    [:macos :arm64] "aarch64"
                    [:windows :amd64] "amd64"
                    (throw (ex-info "Unsupported clj-kondo binary platform"
                                    {:os   (os-key)
                                     :arch (arch-key)})))]
         (str "clj-kondo-" version "-" os "-" arch ".zip")))}

    :cljfmt
    {:repo            "weavejester/cljfmt"
     :archive-format  (if (util/windows?) :zip :tar.gz)
     :version-pattern #"cljfmt\s+v?([^\s]+)"
     :release-tag     identity
     :asset-name
     (fn [version _opts]
       (case [(os-key) (arch-key)]
         [:linux :amd64]   (str "cljfmt-" version "-linux-amd64-static.tar.gz")
         [:linux :arm64]   (str "cljfmt-" version "-linux-aarch64.tar.gz")
         [:macos :arm64]   (str "cljfmt-" version "-darwin-aarch64.tar.gz")
         [:windows :amd64] (str "cljfmt-" version "-win-amd64.zip")
         (throw (ex-info "Unsupported cljfmt binary platform"
                         {:os   (os-key)
                          :arch (arch-key)}))))}

    :tailwindcss
    {:repo            "tailwindlabs/tailwindcss"
     :archive-format  :raw
     :version-pattern #"tailwindcss\s+v?([^\s]+)"
     :release-tag     #(str "v" %)
     :asset-name
     (fn [_version {:keys [asset-name]}]
       (or asset-name
           (case [(os-key) (arch-key)]
             [:linux :amd64]   "tailwindcss-linux-x64"
             [:linux :arm64]   "tailwindcss-linux-arm64"
             [:macos :amd64]   "tailwindcss-macos-x64"
             [:macos :arm64]   "tailwindcss-macos-arm64"
             [:windows :amd64] "tailwindcss-windows-x64.exe"
             (throw (ex-info "Unsupported Tailwind binary platform"
                             {:os   (os-key)
                              :arch (arch-key)})))))}))

(defn- parse-version [tool output]
  (some->> output
           (re-find (:version-pattern (descriptor tool)))
           second))

(defn installed-version [tool command]
  (let [{:keys [exit out err]} (sh/sh command "--version")
        output                 (str/join "\n" (remove str/blank? [out err]))]
    (when (zero? exit)
      (parse-version tool output))))

(defn preferred-bin-path [tool]
  (or (when (local-bin-installed? tool)
        (local-bin-path tool))
      (system-bin-path tool)))

(defn- delete-recursively! [file]
  (when (.exists file)
    (doseq [path (reverse (file-seq file))]
      (io/delete-file path true))))

(defn- download-to! [tool url dest]
  (let [^HttpURLConnection conn (doto (.openConnection (.toURL (URI. url)))
                                  (.setInstanceFollowRedirects true)
                                  (.setConnectTimeout download-connect-timeout-ms)
                                  (.setReadTimeout download-read-timeout-ms))]
    (try
      (let [status (.getResponseCode conn)]
        (when-not (<= 200 status 299)
          (throw (ex-info (format "Failed to download %s" (name tool))
                          {:tool   tool
                           :status status
                           :url    url})))
        (with-open [in  (.getInputStream conn)
                    out (io/output-stream dest)]
          (io/copy in out)))
      (catch SocketTimeoutException e
        (throw (ex-info (format "Timed out downloading %s" (name tool))
                        {:tool               tool
                         :url                url
                         :connect-timeout-ms download-connect-timeout-ms
                         :read-timeout-ms    download-read-timeout-ms}
                        e)))
      (finally
        (.disconnect conn)))))

(defn- copy-file! [src dest]
  (with-open [in  (io/input-stream src)
              out (io/output-stream dest)]
    (io/copy in out)))

(defn- extract-zip! [archive entry-name dest]
  (with-open [zis (ZipInputStream. (io/input-stream archive))]
    (loop []
      (if-let [entry (.getNextEntry zis)]
        (if (and (not (.isDirectory entry))
                 (= entry-name (.getName (io/file (.getName entry)))))
          (with-open [out (io/output-stream dest)]
            (io/copy zis out))
          (recur))
        (throw (ex-info "Binary archive did not contain expected executable"
                        {:archive    (.getPath archive)
                         :entry-name entry-name}))))))

(defn- extract-tar-gz! [archive entry-name temp-dir dest]
  (util/shell "tar" "-xzf" (.getPath archive) "-C" (.getPath temp-dir))
  (if-some [src (some #(when (and (.isFile %)
                                  (= entry-name (.getName %)))
                         %)
                      (file-seq temp-dir))]
    (copy-file! src dest)
    (throw (ex-info "Binary archive did not contain expected executable"
                    {:archive    (.getPath archive)
                     :entry-name entry-name}))))

(defn install-binary!
  ([tool]
   (install-binary! tool {}))
  ([tool {:keys [version] :as opts}]
   (let [version                                              (or version (default-version tool))
         {:keys [repo archive-format release-tag asset-name]} (descriptor tool)
         asset-name                                           (asset-name version opts)
         url                                                  (str "https://github.com/" repo "/releases/download/"
                                                                   (release-tag version) "/" asset-name)
         dest                                                 (io/file (local-bin-path tool))
         temp-dir                                             (io/file (str (System/getProperty "java.io.tmpdir")
                                                                            "/biff-bin-" (UUID/randomUUID)))
         archive                                              (io/file temp-dir asset-name)]
     (io/make-parents dest)
     (.mkdirs temp-dir)
     (println "Downloading" (name tool) version "...")
     (try
       (case archive-format
         :raw
         (download-to! tool url dest)

         :zip
         (do
           (download-to! tool url archive)
           (extract-zip! archive (executable-name tool) dest))

         :tar.gz
         (do
           (download-to! tool url archive)
           (extract-tar-gz! archive (executable-name tool) temp-dir dest)))
       (.setExecutable dest true)
       (local-bin-path tool)
       (finally
         (delete-recursively! temp-dir))))))

(defn ensure-binary! [tool requested-version]
  (let [current (preferred-bin-path tool)]
    (when (or (nil? current)
              (and requested-version
                   (not= requested-version (installed-version tool current))))
      (install-binary! tool {:version (or requested-version
                                          (default-version tool))}))
    (or (do
          (ensure-local-executable! tool)
          (when (local-bin-installed? tool)
            (local-bin-path tool)))
        (system-bin-path tool)
        (throw (ex-info "Expected binary to be installed"
                        {:tool tool})))))

(defn ensure-local-binary!
  ([tool]
   (ensure-local-binary! tool nil {}))
  ([tool requested-version]
   (ensure-local-binary! tool requested-version {}))
  ([tool requested-version opts]
   (let [current (when (local-bin-installed? tool)
                   (local-bin-path tool))]
     (when (or (nil? current)
               (and requested-version
                    (not= requested-version (installed-version tool current))))
       (install-binary! tool (merge opts
                                    {:version (or requested-version
                                                  (default-version tool))})))
     (ensure-local-executable! tool)
     (or (when (local-bin-installed? tool)
           (local-bin-path tool))
         (throw (ex-info "Expected local binary to be installed"
                         {:tool tool}))))))

(defn tailwind-command []
  (let [{:biff.tasks/keys [tailwind-build tailwind-version]} (util/read-config)]
    (if tailwind-version
      (let [command (ensure-binary! :tailwindcss tailwind-version)]
        {:tailwind-cmd (if (= command (local-bin-path :tailwindcss))
                         :local-bin
                         :system-bin)
         :command      [command]})
      (cond
        (util/bun-pkg-installed? "tailwindcss")
        {:tailwind-cmd :bun
         :command      ["bunx" "tailwindcss"]}

        (util/sh-success? "npm" "list" "tailwindcss")
        {:tailwind-cmd :npm
         :command      ["npx" "tailwindcss"]}

        (local-bin-installed? :tailwindcss)
        {:tailwind-cmd :local-bin
         :command      [(do
                          (ensure-local-executable! :tailwindcss)
                          (local-bin-path :tailwindcss))]}

        (system-bin-path :tailwindcss)
        {:tailwind-cmd :system-bin
         :command      [(system-bin-path :tailwindcss)]}

        :else
        {:tailwind-cmd :local-bin
         :command      [(install-binary! :tailwindcss
                                         (cond-> {:version (default-version :tailwindcss)}
                                           tailwind-build
                                           (assoc :asset-name (str "tailwindcss-" tailwind-build))))]}))))
