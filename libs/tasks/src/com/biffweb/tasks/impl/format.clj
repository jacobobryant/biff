(ns com.biffweb.tasks.impl.format
  (:refer-clojure :exclude [format])
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [com.biffweb.stuff.bin :as stuff.bin]
            [com.biffweb.tasks.impl.util :as util]
            [rewrite-clj.zip :as z]))

(def ^:private binding-forms
  '#{binding doseq dotimes for if-let if-some let letfn loop
     when-first when-let when-some with-open with-redefs})

(defn- child-locs [loc]
  (when-let [child (z/down loc)]
    (take-while some? (iterate z/right child))))

(defn- pair-locs [loc]
  (let [children (vec (child-locs loc))]
    (when (even? (count children))
      (partition 2 children))))

(defn- expanded? [[left right]]
  (< (:end-row (meta (z/node left)))
     (:row (meta (z/node right)))))

(defn- separated? [[_ left-right] [right-left _]]
  (< (inc (:end-row (meta (z/node left-right))))
     (:row (meta (z/node right-left)))))

(defn- pair-findings [path pairs]
  (for [[left right] (partition 2 1 pairs)
        :when        (and (or (expanded? left) (expanded? right))
                          (not (separated? left right)))
        :let         [[loc _] right
                      {:keys [row col]} (meta (z/node loc))]]
    {:path path :row row :col col}))

(defn- sibling-index [loc]
  (loop [loc   loc
         index 0]
    (if-let [left (z/left loc)]
      (recur left (inc index))
      index)))

(defn- binding-vector? [loc]
  (let [parent (z/up loc)]
    (and (= :list (some-> parent z/tag))
         (= 1 (sibling-index loc))
         (contains? binding-forms (some-> parent z/down z/sexpr)))))

(defn- cond-form? [loc]
  (and (= :list (z/tag loc))
       (= 'cond (some-> loc z/down z/sexpr))))

(defn- findings-at [path loc]
  (let [pairs (cond
                (= :map (z/tag loc)) (pair-locs loc)
                (binding-vector? loc) (pair-locs loc)

                (cond-form? loc)
                (let [children (rest (child-locs loc))]
                  (when (even? (count children))
                    (partition 2 children))))]
    (when-some [pairs pairs]
      (pair-findings path pairs))))

(defn pair-spacing-findings [path source]
  (loop [loc      (z/of-string source {:track-position? true})
         findings []]
    (if (z/end? loc)
      findings
      (recur (z/next loc)
             (into findings (findings-at path loc))))))

(defn fix-pair-spacing [source]
  (let [rows    (->> (pair-spacing-findings nil source)
                     (map :row)
                     distinct
                     (sort >))
        starts  (into [0]
                      (keep-indexed (fn [index char]
                                      (when (= \newline char)
                                        (inc index))))
                      source)
        newline (if (str/includes? source "\r\n") "\r\n" "\n")]
    (reduce (fn [source row]
              (let [offset (nth starts (dec row))]
                (str (subs source 0 offset) newline (subs source offset))))
            source
            rows)))

(defn- fix-pair-spacing! [file]
  (let [source (slurp file)
        fixed  (fix-pair-spacing source)]
    (when (not= source fixed)
      (spit file fixed))))

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
    (run! fix-pair-spacing! paths)
    (let [version (:biff.tasks/cljfmt-version (util/read-config))
          binary  (ensure-cljfmt-binary! version)]
      (apply util/shell (concat [binary "fix" "--parallel"] paths))))
  nil)
