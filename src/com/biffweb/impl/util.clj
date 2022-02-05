(ns com.biffweb.impl.util
  (:require [clojure.java.shell :as shell]
            [clojure.pprint :as pp]
            [clojure.repl :as repl]
            [clojure.set :as set]
            [clojure.spec.alpha :as spec]
            [clojure.string :as str]
            [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.reload :as reload]
            clojure.tools.namespace.repl
            [clojure.tools.namespace.track :as track]))

(defn ppr-str [x]
  (with-out-str
    (binding [*print-namespace-maps* false]
      (pp/pprint x))))

(defn pprint [x]
  (binding [*print-namespace-maps* false]
    (pp/pprint x))
  (flush))

; todo break this into different namespaces or something

;;;; eval on write

;; Taken from https://github.com/jakemcc/reload
;; TODO is it ok to copy source here since it's EPL? Need to include license?

(defonce global-tracker (atom (track/tracker)))

(def ^:private remove-disabled #'clojure.tools.namespace.repl/remove-disabled)

(defn- print-pending-reloads [tracker]
  (when-let [r (seq (::track/load tracker))]
    (prn :reloading r)))

(defn- print-and-return [tracker]
  (if-let [e (::reload/error tracker)]
    (do (when (thread-bound? #'*e)
          (set! *e e))
        (prn :error-while-loading (::reload/error-ns tracker))
        (repl/pst e)
        e)
    (prn :ok)))

(defn eval-files* [tracker directories]
  (let [new-tracker (apply dir/scan tracker directories)
        new-tracker (remove-disabled new-tracker)]
    (print-pending-reloads new-tracker)
    (let [new-tracker (reload/track-reload (assoc new-tracker ::track/unload []))]
      (print-and-return new-tracker)
      new-tracker)))

;;;; date fns

(def rfc3339 "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

(defn parse-format-date [date in-format out-format]
  (cond->> date
    in-format (.parse (new java.text.SimpleDateFormat in-format))
    out-format (.format (new java.text.SimpleDateFormat out-format))))

(defn parse-date
  ([date]
   (parse-date date rfc3339))
  ([date in-format]
   (parse-format-date date in-format nil)))

(defn format-date
  ([date]
   (format-date date rfc3339))
  ([date out-format]
   (parse-format-date date nil out-format)))

(defn crop-date [d fmt]
  (-> d
      (format-date fmt)
      (parse-date fmt)))

(defn crop-day [t]
  ; will this depend on current timezone?
  (crop-date t "yyyy-MM-dd"))

(defn- expand-time [x]
  (if (= x :now)
    (java.util.Date.)
    x))

(defn seconds-between [t1 t2]
  (quot (- (inst-ms (expand-time t2)) (inst-ms (expand-time t1))) 1000))

(defn duration [x unit]
  (case unit
    :seconds x
    :minutes (* x 60)
    :hours (* x 60 60)
    :days (* x 60 60 24)
    :weeks (* x 60 60 24 7)))

(defn elapsed? [t1 t2 x unit]
  (< (duration x unit)
     (seconds-between t1 t2)))

(defn between-hours? [t h1 h2]
  (let [hours (/ (mod (quot (inst-ms t) (* 1000 60))
                      (* 60 24))
                 60.0)]
    (if (< h1 h2)
      (<= h1 hours h2)
      (or (<= h1 hours)
          (<= hours h2)))))

(defn add-seconds [date seconds]
  (java.util.Date/from (.plusSeconds (.toInstant date) seconds)))

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

(defn base64-encode [bs]
  (.encodeToString (java.util.Base64/getEncoder) bs))

(defn base64-decode [s]
  (.decode (java.util.Base64/getDecoder) s))

(defn ns-contains? [nspace sym]
  (and (namespace sym)
    (let [segments (str/split (name nspace) #"\.")]
      (= segments (take (count segments) (str/split (namespace sym) #"\."))))))

(defn select-as [m key-map]
  (-> m
      (select-keys (keys key-map))
      (set/rename-keys key-map)))

(defn select-ns [m nspace]
  (select-keys m (filter #(ns-contains? nspace (symbol %)) (keys m))))

(defn ns-parts [nspace]
  (if (nil? nspace)
    []
    (some-> nspace
            str
            not-empty
            (str/split #"\."))))

(defn select-ns-as [m ns-from ns-to]
  (->> (select-ns m ns-from)
       (map (fn [[k v]]
              (let [new-ns-parts (->> (ns-parts (namespace k))
                                      (drop (count (ns-parts ns-from)))
                                      (concat (ns-parts ns-to)))]
                [(if (empty? new-ns-parts)
                   (keyword (name k))
                   (keyword (str/join "." new-ns-parts) (name k)))
                 v])))
       (into {})))

(defn assoc-some [m & kvs]
  (->> kvs
       (partition 2)
       (filter (comp some? second))
       (map vec)
       (into m)))

(defn sh
  "Runs a shell command.

  Returns the output if successful; otherwise, throws an exception."
  [& args]
  (let [result (apply shell/sh args)]
    (if (= 0 (:exit result))
      (:out result)
      (throw (ex-info (:err result) result)))))

(defn random-uuid []
  (java.util.UUID/randomUUID))

(defn now []
  (java.util.Date.))

(defn anomaly? [x]
  (spec/valid? (spec/keys :req [:cognitect.anomalies/category]
                          :opt [:cognitect.anomalies/message])
               x))

(defn anom [category & [message & [opts]]]
  (merge opts
         {:cognitect.anomalies/category (keyword "cognitect.anomalies" (name category))}
         (when message
           {:cognitect.anomalies/message message})))
