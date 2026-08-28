(ns com.biffweb.core.impl.system
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.biffweb.core.impl.validation :as impl.v]))

(defn- default-init [modules-var]
  {:biff.core/on-tx
   (fn [ctx]
     (doseq [on-tx (keep :biff.core/on-tx @modules-var)]
       (on-tx ctx)))})

(defn- safe-merge [& ms]
  (when-some [duplicate-keys (->> (mapcat keys ms)
                                  frequencies
                                  (remove (comp #{1} val))
                                  (mapv key)
                                  not-empty)]
    (impl.v/assertion-error
     "Conflicting keys were returned by multiple :biff.core/init functions:"
     (pr-str duplicate-keys)))
  (apply merge ms))

(defn- init-modules
  [modules-var]
  (->> @modules-var
       impl.v/validate
       (keep :biff.core/init)
       (mapv #(% modules-var))
       impl.v/validate
       (apply safe-merge)
       (merge (default-init modules-var))))

(defn- lifecycle-modules [modules components]
  (let [started-modules (filterv :biff.core/start modules)
        missing-ids     (filterv (comp nil? :biff.core/id) started-modules)
        id-frequencies  (frequencies (keep :biff.core/id started-modules))
        duplicate-ids   (into []
                              (keep (fn [[id n]] (when (< 1 n) id)))
                              id-frequencies)
        component-ids   (filterv keyword? components)

        duplicate-components
        (into []
              (keep (fn [[id n]] (when (< 1 n) id)))
              (frequencies component-ids))

        missing-components
        (filterv (complement (set component-ids)) (keys id-frequencies))

        unknown-components
        (filterv (complement (set (keys id-frequencies))) component-ids)]
    (when (seq missing-ids)
      (impl.v/assertion-error
       "Modules with :biff.core/start must set :biff.core/id."))
    (when (seq duplicate-ids)
      (impl.v/assertion-error "Duplicate :biff.core/id values: "
                              (pr-str duplicate-ids)))
    (when (seq duplicate-components)
      (impl.v/assertion-error "Duplicate keyword components: "
                              (pr-str duplicate-components)))
    (when (seq missing-components)
      (impl.v/assertion-error "Missing keyword components: "
                              (pr-str missing-components)))
    (when (seq unknown-components)
      (impl.v/assertion-error "No modules found for keyword components: "
                              (pr-str unknown-components)))
    (into {} (map (juxt :biff.core/id identity)) started-modules)))

(defn- start-module-component [ctx {:biff.core/keys [start stop]}]
  (let [ctx (start ctx)]
    (cond-> ctx
      stop (update :biff.core/stop-system
                   (fn [stop-system]
                     #(do
                        (stop ctx)
                        (stop-system)))))))

;; Maintain backwards compatibility with Biff components that still use
;; :biff/stop
(defn- shim-old-component [component]
  (fn [system]
    (let [system* (component system)]
      (-> system*
          (dissoc :biff/stop)
          (assoc :biff.core/stop-system
                 (fn []
                   (doseq [stop-fn (:biff/stop system*)]
                     (stop-fn))
                   (when-some [stop (:biff.core/stop-system system*)]
                     (stop))))))))

(defn start
  ([modules-var components]
   (start {} modules-var components))
  ([initial-system modules-var components]
   (let [modules        (impl.v/validate @modules-var)
         id->module     (lifecycle-modules modules components)
         initial-system (merge (init-modules modules-var)
                               (impl.v/validate initial-system)
                               {:biff.core/stop-system (fn [])})

         system-map
         (reduce (fn [system component]
                   (log/info "starting:"
                             (str/replace (str component) #"@.*" ""))
                   (impl.v/validate
                    (if (keyword? component)
                      (start-module-component system (get id->module component))
                      ((shim-old-component component) system))))
                 initial-system
                 components)]
     (log/info "System started.")
     system-map)))

(defn stop
  [{:keys [biff.core/stop-system]}]
  (when stop-system
    (stop-system)))
