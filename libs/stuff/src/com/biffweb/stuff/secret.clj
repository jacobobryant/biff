(ns com.biffweb.stuff.secret
  (:require [clojure.pprint :as pprint]))

(def ^:private secret-placeholder "#<SecretDelay: redacted>")

(defn secret-delay [value]
  (proxy [clojure.lang.Delay clojure.lang.IFn] [(fn [] value)]
    (toString [] secret-placeholder)
    (invoke [] (force this))
    (applyTo [args]
      (if (seq args)
        (throw (clojure.lang.ArityException.
                (count args)
                "com.biffweb.stuff.secret/secret-delay"))
        (force this)))
    (call [] (force this))
    (run [] (force this) nil)))

(def secret-delay-class
  (class (secret-delay "")))

(defmethod print-method secret-delay-class
  [_ ^java.io.Writer writer]
  (.write writer secret-placeholder))

(defmethod print-dup secret-delay-class
  [_ ^java.io.Writer writer]
  (.write writer secret-placeholder))

(defmethod pprint/simple-dispatch secret-delay-class
  [_]
  (.write *out* secret-placeholder))
