(ns com.biffweb.stuff.bin
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.pprint :as pprint]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [is]])
  (:import [java.net HttpURLConnection SocketTimeoutException URI]
           [java.util UUID]
           [java.util.zip ZipInputStream]))

(def download-connect-timeout-ms 30000)
(def download-read-timeout-ms 300000)
(def bin-dir "target/bin")

(defn platform-info []
  (let [os-name (str/lower-case (System/getProperty "os.name"))
        arch    (str/lower-case (System/getProperty "os.arch"))]
    {:os   (cond
             (str/includes? os-name "windows") :windows
             (str/includes? os-name "linux") :linux
             (str/includes? os-name "mac") :macos)
     :arch (case arch
             ("amd64" "x86_64") :amd64
             ("aarch64" "arm64") :arm64
             nil)}))

(defn check-platform [{:keys [supported-platforms os arch binary version]}]
  (when-not (contains? supported-platforms [os arch])
    (throw (UnsupportedOperationException.
            (str "Unsupported " binary " binary platform: "
                 "Unable to install " binary " version " version
                 " for your platform: "
                 (pr-str {:os os :arch arch})
                 ". You'll need to install it manually and ensure it's on"
                 " the path.")))))

(defn- supported-platform? []
  (let [{:keys [os arch]} (platform-info)]
    (and os arch)))

(defn- executable-name [executable-basename]
  (if (= :windows (:os (platform-info)))
    (str executable-basename ".exe")
    executable-basename))

(defn- local-bin-path [executable-basename]
  (str bin-dir "/" (executable-name executable-basename)))

(defn- local-bin-installed? [executable-basename]
  (.exists (io/file (local-bin-path executable-basename))))

(defn- system-bin-path [executable-basename]
  (let [binary (executable-name executable-basename)
        path   (System/getenv "PATH")]
    (some (fn [dir]
            (let [file (io/file dir binary)]
              (when (and (.exists file) (.isFile file) (.canExecute file))
                (.getPath file))))
          (str/split (or path "") (re-pattern java.io.File/pathSeparator)))))

(defn- set-executable [path]
  (.setExecutable (io/file path) true))

(defn- installed-version [get-version command]
  (try
    (get-version command)
    (catch Exception _ nil)))

(defn- preferred-bin-path
  [executable-basename]
  (or (when (local-bin-installed? executable-basename)
        (local-bin-path executable-basename))
      (system-bin-path executable-basename)))

(defn- delete-recursively! [file]
  (when (.exists file)
    (doseq [path (reverse (file-seq file))]
      (io/delete-file path true))))

(defn- download-to! [tool url dest]
  (let [^HttpURLConnection conn (doto (.openConnection (.toURL (URI. url)))
                                  (.setInstanceFollowRedirects true)
                                  (.setConnectTimeout
                                   download-connect-timeout-ms)
                                  (.setReadTimeout
                                   download-read-timeout-ms))]
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

(defn- install-binary! [{:keys [executable-basename target-version url]}]
  (when-not (supported-platform?)
    (throw (ex-info (str "Unable to infer the correct download URL for "
                         executable-basename
                         ". Please download the binary for "
                         target-version " to "
                         bin-dir "/" (executable-name executable-basename))
                    {})))
  (let [tool     executable-basename
        filename (url-filename url)
        dest     (io/file (local-bin-path executable-basename))
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
          (extract-zip! archive (executable-name executable-basename) dest))

        :tar.gz
        (do
          (download-to! tool url archive)
          (extract-tar-gz! archive
                           (executable-name executable-basename)
                           temp-dir dest)))
      (.setExecutable dest true)
      (str bin-dir "/" (executable-name executable-basename))
      (finally
        (delete-recursively! temp-dir)))))

(defn ensure-binary
  [{:keys [executable-basename target-version get-version install] :as opts}]
  (let [current (preferred-bin-path executable-basename)]
    (when (or (nil? current)
              (and target-version
                   (not= target-version
                         (installed-version get-version current))))
      (let [installed-path (if install
                             (install)
                             (install-binary!
                              (select-keys opts
                                           [:executable-basename
                                            :target-version :url])))
            actual-version (installed-version get-version installed-path)]
        (when (and target-version
                   (not= target-version actual-version))
          (throw (ex-info "Failed to install correct version of binary"
                          {:actual-version      actual-version
                           :executable-basename executable-basename
                           :expected-version    target-version
                           :path                installed-path})))))
    (or (when (local-bin-installed? executable-basename)
          (doto (local-bin-path executable-basename) set-executable))
        (system-bin-path executable-basename)
        (throw (ex-info "Expected binary to be installed"
                        {:executable-basename executable-basename})))))

(def platforms
  (for [os   [:linux :macos :windows]
        arch [:amd64 :arm64]]
    {:os os :arch arch}))

(defn- github-release-urls [fixture-path github-release-url]
  (let [fixture-file (if-let [resource (io/resource fixture-path)]
                       (io/file (.toURI resource))
                       (let [separator (.lastIndexOf fixture-path "/")
                             parent    (subs fixture-path 0 separator)
                             filename  (subs fixture-path (inc separator))]
                         (io/file (.toURI (io/resource parent)) filename)))]
    (when-not (.exists fixture-file)
      (let [urls (->> (slurp github-release-url)
                      (re-seq #"\"browser_download_url\"\s*:\s*\"([^\"]+)\"")
                      (map second)
                      sort
                      vec)]
        (with-open [writer (io/writer fixture-file)]
          (binding [*out*                       writer
                    pprint/*print-right-margin* 1]
            (pprint/pprint urls)))))
    (set (edn/read-string (slurp fixture-file)))))

(defn- supported-urls [url-fn version]
  (into #{}
        (keep (fn [platform]
                (try
                  (url-fn (assoc platform :version version))
                  (catch UnsupportedOperationException _))))
        platforms))

(defn assert-binary-urls
  [{:keys [fixture-path github-release-url unsupported-urls url-fn version]}]
  (let [fixture-urls (github-release-urls fixture-path github-release-url)
        supported    (supported-urls url-fn version)]
    (doseq [url supported]
      (is (contains? fixture-urls url)))
    (is (= unsupported-urls
           (set/difference fixture-urls supported)))))
