(ns com.biffweb.sqlite.impl.bin
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str])
  (:import [java.net HttpURLConnection SocketTimeoutException URI]
           [java.util UUID]
           [java.util.zip ZipInputStream]))

(def download-connect-timeout-ms 30000)
(def download-read-timeout-ms 300000)

(defn platform-info []
  (let [os-name (str/lower-case (System/getProperty "os.name"))
        arch (str/lower-case (System/getProperty "os.arch"))]
    {:os   (cond
             (str/includes? os-name "windows") :windows
             (str/includes? os-name "linux") :linux
             (str/includes? os-name "mac") :macos)
     :arch (case arch
             ("amd64" "x86_64") :amd64
             ("aarch64" "arm64") :arm64
             nil)}))

(defn- supported-platform? []
  (let [{:keys [os arch]} (platform-info)]
    (and os arch)))

(defn- executable-name [{:keys [executable-basename]}]
  (if (= :windows (:os (platform-info)))
    (str executable-basename ".exe")
    executable-basename))

(defn- local-bin-path [{:keys [bin-dir] :as opts}]
  (str bin-dir "/" (executable-name opts)))

(defn- local-bin-installed? [opts]
  (.exists (io/file (local-bin-path opts))))

(defn- system-bin-path [opts]
  (let [binary (executable-name opts)
        path   (System/getenv "PATH")]
    (some (fn [dir]
            (let [file (io/file dir binary)]
              (when (and (.exists file) (.isFile file) (.canExecute file))
                (.getPath file))))
          (str/split (or path "") (re-pattern java.io.File/pathSeparator)))))

(defn- set-executable [path]
  (.setExecutable (io/file path) true))

(defn- installed-version [{:keys [get-version]} command]
  (try
    (get-version command)
    (catch Exception _ nil)))

(defn- preferred-bin-path [opts]
  (or (when (local-bin-installed? opts)
        (local-bin-path opts))
      (system-bin-path opts)))

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
          (throw (ex-info (format "Failed to download %s" tool)
                          {:tool   tool
                           :status status
                           :url    url})))
        (with-open [in  (.getInputStream conn)
                    out (io/output-stream dest)]
          (io/copy in out)))
      (catch SocketTimeoutException e
        (throw (ex-info (format "Timed out downloading %s" tool)
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
  (let [{:keys [exit err]} (sh/sh "tar" "-xzf" (.getPath archive)
                                  "-C" (.getPath temp-dir))]
    (when-not (zero? exit)
      (throw (ex-info "Failed to extract binary archive"
                      {:archive (.getPath archive)
                       :err     err}))))
  (if-some [src (some #(when (and (.isFile %)
                                  (= entry-name (.getName %)))
                         %)
                      (file-seq temp-dir))]
    (copy-file! src dest)
    (throw (ex-info "Binary archive did not contain expected executable"
                    {:archive    (.getPath archive)
                     :entry-name entry-name}))))

(defn- url-filename [url]
  (.getName (io/file (.getPath (URI. url)))))

(defn- archive-format [filename]
  (cond
    (str/ends-with? filename ".tar.gz") :tar.gz
    (str/ends-with? filename ".zip") :zip
    :else :raw))

(defn- install-binary! [{:keys [bin-dir
                                executable-basename
                                target-version
                                url]
                         :as   opts}]
  (when-not (supported-platform?)
    (throw (ex-info (str "Unable to infer the correct download URL for "
                         executable-basename
                         ". Please download the binary for "
                         target-version " to "
                         (:bin-dir opts) "/" (executable-name opts))
                    {})))
  (let [tool     executable-basename
        filename (url-filename url)
        dest     (io/file (local-bin-path opts))
        temp-dir (io/file (str (System/getProperty "java.io.tmpdir")
                               "/biff-bin-" (UUID/randomUUID)))
        archive  (io/file temp-dir filename)]
    (io/make-parents dest)
    (.mkdirs temp-dir)
    (println "Downloading" tool target-version "...")
    (try
      (case (archive-format filename)
        :raw
        (download-to! tool url dest)

        :zip
        (do
          (download-to! tool url archive)
          (extract-zip! archive (executable-name opts) dest))

        :tar.gz
        (do
          (download-to! tool url archive)
          (extract-tar-gz! archive (executable-name opts) temp-dir dest)))
      (.setExecutable dest true)
      (str (or bin-dir "bin") "/" (executable-name opts))
      (finally
        (delete-recursively! temp-dir)))))

(defn ensure-binary! [{:keys [executable-basename target-version] :as opts}]
  (let [current (preferred-bin-path opts)]
    (when (or (nil? current)
              (and target-version
                   (not= target-version (installed-version opts current))))
      (install-binary! opts))
    (or (when (local-bin-installed? opts)
          (doto (local-bin-path opts) set-executable))
        (system-bin-path opts)
        (throw (ex-info "Expected binary to be installed"
                        {:executable-basename executable-basename})))))
