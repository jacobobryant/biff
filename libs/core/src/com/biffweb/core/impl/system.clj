(ns com.biffweb.core.impl.system
  (:require [clojure.tools.logging :as log]
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

(defn- lifecycle-modules [modules start-order]
  (let [started-modules (filterv :biff.core/start modules)
        missing-ids     (filterv (comp nil? :biff.core/id) started-modules)
        id-frequencies  (frequencies (keep :biff.core/id started-modules))
        duplicate-ids   (into []
                              (keep (fn [[id n]] (when (< 1 n) id)))
                              id-frequencies)
        invalid-ids     (filterv (complement qualified-keyword?) start-order)
        module-ids      start-order

        duplicate-module-ids
        (into []
              (keep (fn [[id n]] (when (< 1 n) id)))
              (frequencies module-ids))

        missing-module-ids
        (filterv (complement (set module-ids)) (keys id-frequencies))

        unknown-module-ids
        (filterv (complement (set (keys id-frequencies))) module-ids)]
    (when (seq missing-ids)
      (impl.v/assertion-error
       "Modules with :biff.core/start must set :biff.core/id."))
    (when (seq invalid-ids)
      (impl.v/assertion-error
       "Start order entries must be qualified module IDs: "
       (pr-str invalid-ids)))
    (when (seq duplicate-ids)
      (impl.v/assertion-error "Duplicate :biff.core/id values: "
                              (pr-str duplicate-ids)))
    (when (seq duplicate-module-ids)
      (impl.v/assertion-error "Duplicate module IDs in start order: "
                              (pr-str duplicate-module-ids)))
    (when (seq missing-module-ids)
      (impl.v/assertion-error "Missing module IDs from start order: "
                              (pr-str missing-module-ids)))
    (when (seq unknown-module-ids)
      (impl.v/assertion-error "No modules found for IDs in start order: "
                              (pr-str unknown-module-ids)))
    (into {} (map (juxt :biff.core/id identity)) started-modules)))

(defn- start-module [ctx {:biff.core/keys [start stop]}]
  (let [ctx (start ctx)]
    (cond-> ctx
      stop (update :biff.core/stop-system
                   (fn [stop-system]
                     #(do
                        (stop ctx)
                        (stop-system)))))))

(defn start
  ([modules-var start-order]
   (start {} modules-var start-order))
  ([initial-system modules-var start-order]
   (let [modules        (impl.v/validate @modules-var)
         id->module     (lifecycle-modules modules start-order)
         initial-system (merge (init-modules modules-var)
                               (impl.v/validate initial-system)
                               {:biff.core/stop-system (fn [])})

         system-map
         (reduce (fn [system id]
                   (log/info "starting:" id)
                   (impl.v/validate
                    (start-module system (get id->module id))))
                 initial-system
                 start-order)]
     (log/info "System started.")
     system-map)))

(defn stop
  [{:keys [biff.core/stop-system]}]
  (when stop-system
    (stop-system)))

(defn component-shim [id component-fn]
  {:biff.core/id id

   :biff.core/start
   (fn [ctx]
     (let [ctx (component-fn ctx)]
       (-> ctx
           (dissoc :biff/stop)
           (assoc-in [::component-shim-stops id] (:biff/stop ctx)))))

   :biff.core/stop
   (fn [ctx]
     (doseq [stop-fn (get-in ctx [::component-shim-stops id])]
       (stop-fn)))})
