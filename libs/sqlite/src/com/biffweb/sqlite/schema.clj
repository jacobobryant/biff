(ns com.biffweb.sqlite.schema
  (:refer-clojure :exclude [int ref]))

(def ? {:optional true})

(defn- base [args]
  (let [opts  (if (map? (last args)) (last args) {})
        flags (if (map? (last args)) (butlast args) args)]
    (when-not (every? #{:required :unique :index} flags)
      (throw (ex-info "Expected :required, :unique, or :index flags followed by an options map"
                      {:args args})))
    (into opts (map (fn [flag] [flag true])) flags)))

(def pkey {:type :uuid :primary-key true})

(defn ref [target & args]
  (assoc (base args) :ref target :type :uuid))

(defn enum [enum-values & args]
  (assoc (base args) :type :enum :enum-values enum-values))

(defn edn [extra-schema & args]
  (assoc (base args) :type :edn :extra-schema extra-schema))

(defn text [& args] (assoc (base args) :type :text))
(defn inst [& args] (assoc (base args) :type :inst))
(defn int [& args] (assoc (base args) :type :int))
(defn bool [& args] (assoc (base args) :type :boolean))
(defn blob [& args] (assoc (base args) :type :blob))
(defn uuid [& args] (assoc (base args) :type :uuid))
(defn real [& args] (assoc (base args) :type :real))
