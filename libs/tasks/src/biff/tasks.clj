(ns biff.tasks
  (:require
    [buddy.core.nonce :as nonce]
    [clojure.java.io :as io]
    [clojure.java.shell :as shell]
    [clojure.string :as str]
    [biff.util :as bu]
    [selmer.parser :as selmer]))

(defn prompt [{:keys [msg default] :as opts}]
  (print msg)
  (flush)
  (or (not-empty (read-line))
    default
    (recur opts)))

(defn get-opts [opts key-infos]
  (reduce (fn [opts {:keys [k msg f] :as key-info}]
            (cond
              (contains? opts k) opts
              f (do
                  (println msg)
                  (assoc opts k (f)))
              :default (assoc opts k (prompt key-info))))
    opts
    key-infos))

(defn latest-biff-sha []
  (-> (bu/sh "git" "ls-remote" "https://github.com/jacobobryant/biff.git" "HEAD")
    (str/split #"\s+")
    first))

(defn add-derived [{:keys [main-ns] :as opts}]
  (let [parent-ns (str/replace (str main-ns) #"(\.core)$" "")
        parent-path (str/replace parent-ns "." "/")
        main-ns-path (str/replace (str main-ns) "." "/")]
    (assoc opts
      :parent-ns parent-ns
      :parent-path parent-path
      :main-ns-path main-ns-path)))

(defn copy-files [{:keys [template-root dir] :as opts}]
  (let [src-file-prefix (str (.getPath (io/file (io/resource template-root))) "/")]
    (doseq [src-file (filter #(.isFile %)
                       (file-seq (io/file (io/resource template-root))))
            :let [src-file-postfix (-> src-file
                                     .getPath
                                     (str/replace-first src-file-prefix ""))
                  dest-path (str dir "/"
                                 (-> src-file-postfix
                                     (selmer/render opts)
                                     ; We add _ to clojure template file paths so that they don't
                                     ; get eval'd by biff.util/refresh.
                                     (str/replace #"_$" "")))]]
      (io/make-parents dest-path)
      (spit dest-path (selmer/render (slurp src-file) opts)))))

(defn generate-key [length]
  (bu/base64-encode (nonce/random-bytes length)))

(defn new-project []
  (println "Creating a new Biff project.")
  (let [{:keys [dir] :as opts}
        (-> opts
          (get-opts [{:k :sha
                      :msg "Fetching latest Biff version..."
                      :f latest-biff-sha}
                     {:k :dir
                      :msg "Enter name for project directory: "}
                     {:k :main-ns
                      :msg "Enter main namespace (e.g. example.core): "}])
          (update :sha str)
          (update :dir str)
          (update :main-ns symbol)
          add-derived
          (assoc :jwt-secret (generate-key 32)
                 :cookie-secret (generate-key 16)
                 :template-root "biff/template/"))]
    (copy-files opts)
    (bu/sh "chmod" "+x" (str dir "/task"))
    (doseq [f (file-seq (io/file dir "config"))]
      (bu/sh "chmod" (if (.isFile f) "600" "700") (.getPath f)))
    (println)
    (println "Your project is ready. Run the following commands to get started:")
    (println)
    (println "  cd" dir)
    (println "  git init")
    (println "  ./task init")
    (println "  ./task dev")
    (System/exit 0)))
