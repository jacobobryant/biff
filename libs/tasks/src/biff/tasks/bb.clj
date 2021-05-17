(ns biff.tasks.bb
  (:require [babashka.fs :as fs]
            [babashka.process :refer [process check]]
            [bencode.core :as bencode]
            [clojure.edn :as edn]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]))

(defn print-throwable-map
  [{:keys [trace]
    [{:keys [type message data]}] :via}]
  (printf "%s: %s\n" type message)
  (some-> data prn)
  (doseq [[i [cls method filename line]] (map-indexed vector trace)]
    (printf " %s %s.%s (%s:%s)\n"
            (if (= i 0) "at" "  ")
            cls method filename line)))

(defn nrepl-eval [port expr]
  (let [s (java.net.Socket. "localhost" port)
        out (.getOutputStream s)
        in (java.io.PushbackInputStream. (.getInputStream s))]
    (bencode/write-bencode
      out
      {"op" "eval"
       "code" expr
       "nrepl.middleware.caught/print?" "true"})
    (loop []
      (let [{:strs [out
                    value
                    status
                    nrepl.middleware.caught/throwable]
             :as result} (bencode/read-bencode in)]
        (some-> out String. print)
        (some->> throwable
                 String.
                 (edn/read-string {:readers {'error identity}})
                 print-throwable-map)
        (some-> value String. println)
        (when-not (or value status)
          (recur))))))

(defn sh
  "Runs a shell command.

  Returns the output if successful; otherwise, throws an exception."
  [& args]
  (let [args (if (and (= (count (take-while string? args)) 1)
                      (str/includes? (first args) " "))
               (str/split (first args) #"\s+")
               args)
        result (apply shell/sh args)]
    (if (= 0 (:exit result))
      (:out result)
      (throw (ex-info (:err result) result)))))

(defn get-ancestors [get-parents children]
  (into (set children)
    (some->> children
      (mapcat get-parents)
      distinct
      not-empty
      (get-ancestors get-parents))))

(defn libs:sync* [{:keys [dev]}]
  (let [{:keys [libs deps git-url group-id]} (edn/read-string (slurp "libs.edn"))
        sha (str/trim (sh "git rev-parse HEAD"))]
    (doseq [[proj-name config] libs
            :let [dir (fs/file "libs" (str proj-name))
                  proj-ancestors (get-ancestors
                                   #(get-in libs [% :libs])
                                   (:libs config))
                  paths (filterv #(fs/directory? (fs/file dir %))
                                 ["src" "resources"])
                  proj-deps (merge
                              (when (not-empty paths)
                                {:paths paths})
                              {:deps (into
                                       (select-keys deps (:deps config))
                                       (for [p proj-ancestors]
                                         [(symbol (str group-id) (str p))
                                          (if dev
                                            {:local/root (str "../" p)}
                                            {:git/url git-url
                                             :deps/root (str "libs/" p)
                                             :sha sha})]))})]]
      (when-not (fs/directory? dir)
        (fs/create-dirs dir))
      (spit (fs/file dir "deps.edn") (with-out-str (pprint proj-deps))))))

(defn sync-libs-dev []
  (libs:sync* {:dev true}))

(defn sync-libs []
  (libs:sync* {}))

(def ^:private source-uri
  "https://github.com/jacobobryant/biff/blob/{git-commit}/{filepath}#L{line}")

(defn build-codox []
  (let [{:keys [libs]} (edn/read-string (slurp "libs.edn"))
        doc-libs (get-ancestors #(get-in libs [% :libs])
                                (get-in libs ['docs :libs]))
        source-paths (map #(str "libs/" % "/src") doc-libs)
        deps '{:deps {codox/codox {:mvn/version "0.10.7"}
                      biff/docs {:local/root "libs/docs"}}}]
    (sh "rm" "-rf" "site/codox")
    (check (process (into ["clj"
                           "-Sdeps" (pr-str deps)
                           "-X" "codox.main/generate-docs"]
                          (mapcat (fn [[k v]]
                                    [k (pr-str v)])
                                  {:source-paths source-paths
                                   :source-uri source-uri
                                   :output-path "site/codox"}))
                    {:out :inherit
                     :err :inherit}))
    nil))
