(ns biff.util.static)

(defn copy-resources [src-root dest-root]
  (let [resource-root (io/resource src-root)
        files (->> resource-root
                io/as-file
                file-seq
                (filter #(.isFile %))
                (map #(subs (.getPath %) (count (.getPath resource-root)))))]
    (doseq [f files
            :let [src (str (.getPath resource-root) f)
                  dest (str dest-root f)]]
      (io/make-parents dest)
      (io/copy (io/file src) (io/file dest)))))

; you could say that rum is one of our main exports
(defn export-rum [pages dir]
  (doseq [[path form] pages
          :let [full-path (cond-> (str dir path)
                            (str/ends-with? path "/") (str "index.html"))]]
    (io/make-parents full-path)
    (spit full-path (rum/render-static-markup form))))

