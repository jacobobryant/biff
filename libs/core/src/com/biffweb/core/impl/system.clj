(ns com.biffweb.core.impl.system
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.biffweb.core.impl.validation :as impl.v]))

(defn module
  {:biff.core/init
   (fn [modules-var]
     {:biff.core/on-tx
      (fn [ctx]
        (doseq [on-tx (keep :biff.core/on-tx @modules-var)]
          (on-tx ctx)))})})

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
       (apply safe-merge)))

(defn- shim-old-component
  "Maintain backwards compatibility with Biff components that still use :biff/stop"
  [component]
  (fn [system]
    (let [system* (component system)]
      (-> system*
          (dissoc :biff/stop)
          (update :biff.core/stop into (reverse (:biff/stop system*)))))))

(defn start
  ([modules-var components]
   (start {} modules-var components))
  ([initial-system modules-var components]
   (let [initial-system (merge (init-modules modules-var)
                               (impl.v/validate initial-system)
                               {:biff.core/stop []})

         system-map
         (reduce (fn [system component]
                   (log/info "starting:" (str/replace (str component) #"@.*" ""))
                   (impl.v/validate ((shim-old-component component) system)))
                 initial-system
                 components)]
     (log/info "System started.")
     system-map)))

(defn stop
  [system]
  (doseq [stop-fn (reverse (:biff.core/stop system))]
    (stop-fn)))
