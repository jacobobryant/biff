(ns com.biffweb.tasks.generate
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- new-secret [length]
  (let [buffer (byte-array length)]
    (.nextBytes (java.security.SecureRandom/getInstanceStrong) buffer)
    (.encodeToString (java.util.Base64/getEncoder) buffer)))

(defn- project-root []
  (io/file (System/getProperty "user.dir")))

(defn- template-path [resource-name]
  (let [resource-file (io/file (project-root) "resources" resource-name)]
    (cond
      (io/resource resource-name) [:resource resource-name]
      (.exists resource-file) [:file resource-file]
      :else (throw (ex-info "Config template not found"
                            {:resource resource-name
                             :path     (.getPath resource-file)})))))

(defn- render-config-template [resource-name]
  (-> (let [[source path] (template-path resource-name)]
        (case source
          :resource (slurp (io/resource path))
          :file (slurp path)))
      (str/replace #"\{\{\s+new-secret\s+(\d+)\s+\}\}"
                   (fn [[_ n]]
                     (new-secret (parse-long n))))))

(defn ensure-config-files
  "Creates any missing config.env/config.prod.env files."
  []
  (let [targets [{:path     "config.env"
                  :resource "TEMPLATE.config.env"}
                 {:path     "config.prod.env"
                  :resource "TEMPLATE.config.prod.env"}]
        created (reduce (fn [created {:keys [path resource]}]
                          (let [target-file (io/file (project-root) path)]
                            (if (.exists target-file)
                              created
                              (do
                                (spit target-file (render-config-template resource))
                                (conj created path)))))
                        []
                        targets)]
    (when (seq created)
      (println "Generated" (str/join " and " created) "."))))
