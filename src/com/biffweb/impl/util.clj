(ns com.biffweb.impl.util
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.pprint :as pp]
            [clojure.spec.alpha :as spec]
            [clojure.stacktrace :as st]
            [clojure.string :as str]
            [clojure.tools.deps.alpha.repl :as deps-repl]
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.repl :as tn-repl]
            [clojure.walk :as walk]
            [com.biffweb.impl.time :as time])
  (:import [clojure.lang DynamicClassLoader]))

(defmacro catchall-verbose
  [& body]
  `(try
     ~@body
     (catch Exception e#
       (st/print-stack-trace e#))))

(defn start-system [system-atom init]
  (reset! system-atom (merge {:biff/stop '()} init))
  (loop [{[f & components] :biff/components :as ctx} init]
    (when (some? f)
      (log/info "starting:" (str f))
      (recur (reset! system-atom (f (assoc ctx :biff/components components))))))
  (log/info "System started.")
  @system-atom)

(defn refresh [{:keys [biff/after-refresh biff/stop]}]
  (doseq [f stop]
    (log/info "stopping:" (str f))
    (f))
  (tn-repl/refresh :after after-refresh))

(defn add-libs []
  (let [cl (.getContextClassLoader (Thread/currentThread))]
    (when-not (instance? DynamicClassLoader cl)
      (.setContextClassLoader (Thread/currentThread) (DynamicClassLoader. cl))))
  (deps-repl/add-libs (:deps (edn/read-string (slurp "deps.edn")))))

(defn ppr-str [x]
  (with-out-str
    (binding [*print-namespace-maps* false]
      (pp/pprint x))))

(defn pprint [x]
  (binding [*print-namespace-maps* false]
    (pp/pprint x))
  (flush))

(defn base64-encode [bs]
  (.encodeToString (java.util.Base64/getEncoder) bs))

(defn base64-decode [s]
  (.decode (java.util.Base64/getDecoder) s))

(defn sha256 [string]
  (let [digest (.digest (java.security.MessageDigest/getInstance "SHA-256") (.getBytes string "UTF-8"))]
    (apply str (map (partial format "%02x") digest))))

(defn assoc-some [m & kvs]
  (->> kvs
       (partition 2)
       (filter (comp some? second))
       (map vec)
       (into m)))

(defn safe-merge [& ms]
  (reduce (fn [m1 m2]
            (let [dupes (filter #(contains? m1 %) (keys m2))]
              (when (not-empty dupes)
                (throw (ex-info (str "Maps contain duplicate keys: " (str/join ", " dupes))
                                {:keys dupes})))
              (merge m1 m2)))
          {}
          ms))

(defn sh [& args]
  (let [result (apply shell/sh args)]
    (if (= 0 (:exit result))
      (:out result)
      (throw (ex-info (:err result) result)))))

(defn read-config [path]
  (let [env (keyword (or (System/getenv "BIFF_ENV") "prod"))
        env->config (edn/read-string (slurp path))
        config-keys (concat (get-in env->config [env :merge]) [env])
        config (apply merge (map env->config config-keys))]
    config))

(defn use-when [f & components]
  (fn [ctx]
    (if (f ctx)
      (reduce (fn [system component]
                (log/info "starting:" (str component))
                (component system))
              ctx
              components)
      ctx)))

(defn anomaly? [x]
  (spec/valid? (spec/keys :req [:cognitect.anomalies/category]
                          :opt [:cognitect.anomalies/message])
               x))

(defn anom [category & [message & [opts]]]
  (merge opts
         {:cognitect.anomalies/category (keyword "cognitect.anomalies" (name category))}
         (when message
           {:cognitect.anomalies/message message})))

(def http-status->msg
  {100 "Continue"
   101 "Switching Protocols"
   102 "Processing"
   200 "OK"
   201 "Created"
   202 "Accepted"
   203 "Non-Authoritative Information"
   204 "No Content"
   205 "Reset Content"
   206 "Partial Content"
   207 "Multi-Status"
   208 "Already Reported"
   226 "IM Used"
   300 "Multiple Choices"
   301 "Moved Permanently"
   302 "Found"
   303 "See Other"
   304 "Not Modified"
   305 "Use Proxy"
   306 "Reserved"
   307 "Temporary Redirect"
   308 "Permanent Redirect"
   400 "Bad Request"
   401 "Unauthorized"
   402 "Payment Required"
   403 "Forbidden"
   404 "Not Found"
   405 "Method Not Allowed"
   406 "Not Acceptable"
   407 "Proxy Authentication Required"
   408 "Request Timeout"
   409 "Conflict"
   410 "Gone"
   411 "Length Required"
   412 "Precondition Failed"
   413 "Request Entity Too Large"
   414 "Request-URI Too Long"
   415 "Unsupported Media Type"
   416 "Requested Range Not Satisfiable"
   417 "Expectation Failed"
   422 "Unprocessable Entity"
   423 "Locked"
   424 "Failed Dependency"
   425 "Unassigned"
   426 "Upgrade Required"
   427 "Unassigned"
   428 "Precondition Required"
   429 "Too Many Requests"
   430 "Unassigned"
   431 "Request Header Fields Too Large"
   500 "Internal Server Error"
   501 "Not Implemented"
   502 "Bad Gateway"
   503 "Service Unavailable"
   504 "Gateway Timeout"
   505 "HTTP Version Not Supported"
   506 "Variant Also Negotiates (Experimental)"
   507 "Insufficient Storage"
   508 "Loop Detected"
   509 "Unassigned"
   510 "Not Extended"
   511 "Network Authentication Required"})

(defn default-on-error [{:keys [status]}]
  {:status status
   :headers {"content-type" "text/html"}
   :body (str "<h1>" (http-status->msg status) "</h1>")})

(defn- wrap-deref [form syms]
  (walk/postwalk (fn [sym]
                   (if (contains? syms sym)
                     `(deref ~sym)
                     sym))
                 form))

(defn letd* [bindings & body]
  (let [[bindings syms] (->> bindings
                             destructure
                             (partition 2)
                             (reduce (fn [[bindings syms] [sym form]]
                                       [(into bindings [sym `(delay ~(wrap-deref form syms))])
                                        (conj syms sym)])
                                     [[] #{}]))]
    `(let ~bindings
       ~@(wrap-deref body syms))))

(defn fix-print* [& body]
  `(binding [*out* (alter-var-root #'*out* identity)
             *err* (alter-var-root #'*err* identity)
             *flush-on-newline* (alter-var-root #'*flush-on-newline* identity)]
     ~@body))

(defn delete-old-files [{:keys [dir exts age-seconds]
                         :or {age-seconds 30}}]
  (doseq [file (file-seq (io/file dir))
          :when (and (.isFile file)
                     (time/elapsed? (java.util.Date. (.lastModified file))
                                    :now
                                    age-seconds
                                    :seconds)
                     (or (empty? exts)
                         (some #(str/ends-with? (.getPath file) %) exts)))]
    (log/info "deleting" file)
    (io/delete-file file)))

(defn join [sep xs]
  (rest (mapcat vector (repeat sep) xs)))

(defn normalize-email [email]
  (some-> email str/trim str/lower-case not-empty))
