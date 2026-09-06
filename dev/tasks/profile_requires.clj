(ns tasks.profile-requires)

(defn- loaded-lib? [prefix lib options]
  (let [lib    (if prefix (symbol (str prefix \. lib)) lib)
        opts   (apply hash-map options)
        loaded @(var-get (ns-resolve 'clojure.core '*loaded-libs*))]
    (and (contains? loaded lib)
         (not (:reload opts))
         (not (:reload-all opts)))))

(defn- lib-symbol [prefix lib]
  (if prefix
    (symbol (str prefix \. lib))
    lib))

(defn- children-by-parent [entries]
  (group-by :parent entries))

(defn- self-time [entry children]
  (- (:elapsed entry)
     (reduce + 0 (map :elapsed (children (:id entry))))))

(defn- column-width [heading values]
  (reduce max (count heading) (map (comp count #(format "%.1f" %)) values)))

(defn- print-entry [entry children widths prefix last? root?]
  (let [children' (sort-by (comp - :elapsed) (children (:id entry)))
        self      (self-time entry children)
        branch    (if root? "" (if last? "└─ " "├─ "))]
    (printf (str "%" (:elapsed widths) ".1f  %" (:self widths) ".1f  %s%s%s\n")
            (:elapsed entry) self prefix branch (:lib entry))
    (doseq [[idx child] (map-indexed vector children')]
      (print-entry child
                   children
                   widths
                   (str prefix (when-not root? (if last? "   " "│  ")))
                   (= idx (dec (count children')))
                   false))))

(defn- print-profile [entries]
  (let [children   (children-by-parent entries)
        roots      (sort-by (comp - :elapsed) (children nil))
        total      (reduce + 0 (map :elapsed roots))
        self-width (column-width "self ms"
                                 (map #(self-time % children) entries))
        widths     {:elapsed (column-width "elapsed ms" (map :elapsed entries))
                    :self    self-width}]
    (println)
    (printf "Require profile: %.1f ms\n\n" total)
    (printf (str "%" (:elapsed widths) "s  %" (:self widths) "s  %s\n")
            "elapsed ms" "self ms" "namespace")
    (doseq [[idx root] (map-indexed vector roots)]
      (print-entry root children widths "" (= idx (dec (count roots))) true))))

(defn profile-main [lib]
  (let [entries  (atom [])
        next-id  (atom 0)
        stack    (ThreadLocal.)
        load-var (ns-resolve 'clojure.core 'load-lib)
        load-lib (var-get load-var)]
    (alter-var-root
     load-var
     (constantly
      (fn [prefix lib & options]
        (if (loaded-lib? prefix lib options)
          (apply load-lib prefix lib options)
          (let [id      (swap! next-id inc)
                parents (or (.get stack) [])
                start   (System/nanoTime)]
            (.set stack (conj parents id))
            (try
              (apply load-lib prefix lib options)
              (finally
                (swap! entries conj {:id      id
                                     :parent  (peek parents)
                                     :lib     (lib-symbol prefix lib)
                                     :elapsed (/ (- (System/nanoTime) start)
                                                 1000000.0)})
                (.set stack parents))))))))
    (try
      (require lib)
      (finally
        (alter-var-root load-var (constantly load-lib))
        (print-profile @entries)))))

(defn profile-requires [namespace]
  (let [lib       (symbol namespace)
        main-opts ["-i" "dev/tasks/profile_requires.clj"
                   "-e" (str "(tasks.profile-requires/profile-main '" lib ")")]
        deps      (pr-str {:aliases
                           {:biff/profile-requires
                            {:main-opts main-opts}}})
        command   ["clj" "-Sdeps" deps "-M:run:biff/profile-requires"]
        process   (-> (ProcessBuilder. command)
                      .inheritIO
                      .start)
        exit      (.waitFor process)]
    (when-not (zero? exit)
      (throw (ex-info "Require profiling failed" {:exit exit :namespace lib})))
    nil))
