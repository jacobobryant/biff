(ns com.biffweb.ring.impl.path
  (:require [clojure.string :as str]
            [com.biffweb.core :as biff.core]
            [ring.util.codec :as codec]
            [taoensso.nippy :as nippy])
  (:import [java.nio ByteBuffer]
           [java.util Base64 UUID]))

(biff.core/register
 {::query-params 'map?})

(defn testing? []
  @(requiring-resolve 'com.biffweb.ring/*testing*))

(defn- base64-encode [^bytes bytes]
  (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) bytes))

(defn base64-decode [^String s]
  (.decode (Base64/getUrlDecoder) s))

(defn- encode-path-param [x]
  (if (uuid? x)
    (let [buf (ByteBuffer/allocate 16)]
      (.putLong buf (.getMostSignificantBits ^UUID x))
      (.putLong buf (.getLeastSignificantBits ^UUID x))
      (base64-encode (.array buf)))
    (str x)))

(defn decode-uuid [x]
  (if-not (and (string? x) (= 22 (count x)))
    x
    (try
      (let [bytes (base64-decode x)]
        (if (= 16 (alength bytes))
          (let [buf (ByteBuffer/wrap bytes)]
            (UUID. (.getLong buf) (.getLong buf)))
          x))
      (catch IllegalArgumentException _
        x))))

(defn route-path [path-or-route]
  (cond
    (string? path-or-route)
    path-or-route

    (and (vector? path-or-route)
         (string? (first path-or-route)))
    (first path-or-route)

    :else
    (throw (ex-info (str "Path helpers expect a string or a reitit-style "
                         "route vector.")
                    {:path-or-route path-or-route}))))

(defn- render-path [path args]
  (loop [segments (str/split path #"/" -1)
         args     (map encode-path-param args)
         rendered []]
    (if-some [segment (first segments)]
      (if (str/starts-with? segment ":")
        (recur (rest segments) (rest args) (conj rendered (first args)))
        (recur (rest segments) args (conj rendered segment)))
      (str/join "/" rendered))))

(defn- path-with-query [path query-params]
  (biff.core/validate {::query-params query-params})
  (if (testing?)
    [path query-params]
    (str path
         "?"
         (codec/form-encode
          {:npy (base64-encode (nippy/fast-freeze query-params))}))))

(defn path [path-or-route & args]
  (let [path             (route-path path-or-route)
        path-param-count (->> (str/split path #"/")
                              (filterv #(str/starts-with? % ":"))
                              count)
        arg-count        (count args)

        _
        (when-not (<= path-param-count arg-count (inc path-param-count))
          (throw (ex-info "Wrong number of args for path."
                          {:path             path
                           :path-param-count path-param-count
                           :arg-count        arg-count})))

        [path-args [query-params]] (split-at path-param-count args)]
    (cond-> (render-path path path-args)
      query-params (path-with-query query-params))))

(defmacro defpath [sym path-str]
  `(def ~sym (partial path ~path-str)))
