(ns com.biffweb.sqlite.schema
  "Helpers for defining columns. All functions take varargs that can include:

   - flags: :unique, :required, and/or :index in any order
   - followed by a map of additional options

   e.g. (s/text :required :unique {:extra-schema ...})"
  (:refer-clojure :exclude [int ref]))

(def
  ^{:doc "Short-hand for {:optional true}. Can be used in Malli schemas."}
  ?
  {:optional true})

(defn- base [args]
  (let [opts  (if (map? (last args)) (last args) {})
        flags (if (map? (last args)) (butlast args) args)]
    (when-not (every? #{:required :unique :index} flags)
      (throw (ex-info (str "Expected :required, :unique, or :index flags "
                           "followed by an options map")
                      {:args args})))
    (into opts (map (fn [flag] [flag true])) flags)))

(def
  ^{:doc "Short-hand for {:type :uuid :primary-key true}"}
  pkey
  {:type :uuid :primary-key true})

(defn ref
  "Returns a map with {:ref target, :type :uuid, ...}"
  [target & args]
  (assoc (base args) :ref target :type :uuid))

(defn enum
  "Returns a map with {:type :enum, :enum-values enum-values, ...}"
  [enum-values & args]
  (assoc (base args) :type :enum :enum-values enum-values))

(defn edn
  "Returns a map with {:type :edn, :extra-schema extra-schema, ...}"
  [extra-schema & args]
  (assoc (base args) :type :edn :extra-schema extra-schema))

(defn text
  "Returns a map with {:type :text, ...}"
  [& args]
  (assoc (base args) :type :text))

(defn inst
  "Returns a map with {:type :inst, ...}"
  [& args]
  (assoc (base args) :type :inst))

(defn int
  "Returns a map with {:type :int, ...}"
  [& args]
  (assoc (base args) :type :int))

(defn bool
  "Returns a map with {:type :boolean, ...}"
  [& args]
  (assoc (base args) :type :boolean))

(defn blob
  "Returns a map with {:type :blob, ...}"
  [& args]
  (assoc (base args) :type :blob))

(defn uuid
  "Returns a map with {:type :uuid, ...}"
  [& args]
  (assoc (base args) :type :uuid))

(defn real
  "Returns a map with {:type :real, ...}"
  [& args]
  (assoc (base args) :type :real))
