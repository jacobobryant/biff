(ns com.biffweb.build.util.lazy
  (:refer-clojure :exclude [refer]))

(defmacro refer [sym & [sym-alias]]
  (let [sym-alias (or sym-alias (symbol (name sym)))]
    `(defn ~sym-alias [& args#]
       (apply (requiring-resolve '~sym) args#))))

(defmacro refer-many [& args]
  `(do
     ~@(for [[ns-sym fn-syms] (partition 2 args)
             fn-sym fn-syms]
         `(refer ~(symbol (name ns-sym) (name fn-sym))))))
