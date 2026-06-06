(ns com.biffweb.datastar
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [com.biffweb.core :as biff.core]
   [com.biffweb.datastar.impl.brotli :as brotli]
   [ring.core.protocols :as rp])
  (:import
   (java.time Duration)
   (java.util.concurrent TimeUnit)
   (java.util.concurrent.locks Condition ReentrantLock)))

(biff.core/register
 {:biff.datastar/condition      [:fn #(instance? Condition %)]
  :biff.datastar/context-window 'integer?
  :biff.datastar/epoch          [:fn #(instance? clojure.lang.IAtom %)]
  :biff.datastar/lock           [:fn #(instance? ReentrantLock %)]
  :biff.datastar/rate-limit-ms  'integer?
  :biff.datastar/signals        'map?
  :biff.datastar/sse-request    'boolean?
  :biff.datastar/tab-id         'string?})

(def ^:private heartbeat-interval (Duration/ofMinutes 5))
(def ^:private default-context-window 18)
(def ^:private default-rate-limit-ms 15)
(def ^:private default-datastar-script-url
  "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.1/bundles/datastar.js")
(def tab-id-js
  "self.crypto.randomUUID().substring(0,8)")

(defn- js-string [s]
  (pr-str s))

(defn- headers-expr [fields]
  (str "({" (str/join ", " fields) "})"))

(def ^:private connection-action
  (let [headers ["'X-Biff-Datastar-Page-Request': 'true'"]
        options (str "{headers: " (headers-expr headers) ", "
                     "openWhenHidden: true, "
                     "retryMaxCount: Infinity}")]
    ;; The throwaway `u` param keeps this expression valid whether or not the
    ;; page already has its own query string; the backend ignores it.
    (str "@get("
         "window.location.pathname + (window.location.search + '&u=').replace(/^&/,'?'), "
         options
         ")")))

(def init-opts
  "Datastar attributes for the element that owns the page-level SSE connection."
  (let [action connection-action]
    {:data-signals:tab-id    tab-id-js
     :data-init              action
     :data-on:online__window action}))

(defn configure-csrf
  "Returns a JS module string that adds the Ring anti-forgery token to Datastar's
  backend actions."
  ([csrf-token]
   (configure-csrf default-datastar-script-url csrf-token))
  ([datastar-script-url csrf-token]
   (str "import { action, actions } from "
        (js-string datastar-script-url)
        ";\n"
        "const methods = ['get', 'post', 'put', 'patch', 'delete'];\n"
        "const originals = Object.fromEntries(methods.map(name => [name, actions[name]]));\n"
        "const csrfHeader = "
        (headers-expr [(str "'X-CSRF-Token': " (js-string csrf-token))])
        ";\n"
        "const mergeOptions = (options = {}) => ({\n"
        "  ...options,\n"
        "  headers: {\n"
        "    ...csrfHeader,\n"
        "    ...(options.headers ?? {}),\n"
        "  },\n"
        "});\n"
        "for (const name of methods) {\n"
        "  const original = originals[name];\n"
        "  if (!original) continue;\n"
        "  action({\n"
        "    name,\n"
        "    apply(ctx, url, options = {}) {\n"
        "      return original(ctx, url, mergeOptions(options));\n"
        "    },\n"
        "  });\n"
        "}\n")))

(defn- signal-key [k]
  (if (or (keyword? k) (string? k) (symbol? k))
    (keyword (name k))
    k))

(defn- normalize-signals [signals]
  (into {}
        (map (fn [[k v]]
               [(signal-key k) v]))
        signals))

(defn- parse-signals [signals]
  (cond
    (nil? signals) nil
    (map? signals) (normalize-signals signals)
    (string? signals) (when-let [signals (some-> signals str/trim not-empty)]
                        (json/read-str signals :key-fn keyword))
    :else
    (throw (ex-info "Datastar request signals must already be parsed into a map or be provided as a JSON string."
                    {:signals-type (type signals)}))))

(defn- request-signals [req]
  (or (get-in req [:params "datastar"])
      (get-in req [:params :datastar])
      (when (= "true" (get-in req [:headers "datastar-request"]))
        (or (not-empty (:form-params req))
            (not-empty (:body-params req))
            (:params req)))))

(defn- attach-signals [req]
  (if-some [signals (some-> (request-signals req) parse-signals)]
    (assoc req :biff.datastar/signals signals)
    req))

(defn new-lock
  "Creates the lock state expected by `refresh` and `wrap-datastar`."
  []
  (let [lock (ReentrantLock.)]
    {:biff.datastar/lock      lock
     :biff.datastar/condition (.newCondition lock)
     :biff.datastar/epoch     (atom 0)}))

(defn refresh
  "Signals all live Datastar SSE loops to rerender."
  [{:biff.datastar/keys [lock condition epoch]}]
  (when-not (and lock condition epoch)
    (throw (ex-info "refresh requires :biff.datastar/lock, :biff.datastar/condition, and :biff.datastar/epoch."
                    {})))
  (.lock ^ReentrantLock lock)
  (try
    (swap! epoch inc)
    (.signalAll ^Condition condition)
    nil
    (finally
      (.unlock ^ReentrantLock lock))))

(defn- attach-tab-id [req]
  (assoc req :biff.datastar/tab-id (get-in req [:biff.datastar/signals :tabId])))

(defn- response-map [response]
  (when-not (map? response)
    (throw (ex-info "Datastar handlers must return Ring response maps."
                    {:response response})))
  response)

(defn- datastar-sse-request? [req]
  (and (= :get (:request-method req))
       (= "true" (get-in req [:headers "x-biff-datastar-page-request"]))))

(defn- patch-elements-event [body]
  (str "event: datastar-patch-elements\n"
       "id: " (Integer/toHexString (hash body)) "\n"
       "data: elements " (str/replace body "\n" "\ndata: elements ")
       "\n\n"))

(defn- wait-for-update! [{:biff.datastar/keys [lock condition epoch]} last-epoch timeout-ms]
  (.lock ^ReentrantLock lock)
  (try
    (loop [remaining (.toNanos TimeUnit/MILLISECONDS (long (max 1 timeout-ms)))]
      (let [current @epoch]
        (cond
          (not= current last-epoch) current
          (<= remaining 0) current
          :else (recur (.awaitNanos ^Condition condition remaining)))))
    (finally
      (.unlock ^ReentrantLock lock))))

(defn- current-request [req]
  (-> req
      attach-signals
      attach-tab-id
      (assoc :biff.datastar/sse-request true)))

(defrecord ^:private StreamResponse [write-fn]
  rp/StreamableResponseBody
  (write-body-to-stream [_ _ output-stream]
    (write-fn output-stream)))

(defn- stream-response-body [handler req]
  (let [context-window (or (:biff.datastar/context-window req) default-context-window)
        rate-limit-ms  (long (or (:biff.datastar/rate-limit-ms req) default-rate-limit-ms))]
    (when-not (pos? rate-limit-ms)
      (throw (ex-info ":biff.datastar/rate-limit-ms must be positive."
                      {:rate-limit-ms rate-limit-ms})))
    (->StreamResponse
     (fn [raw-out]
       (try
         (with-open [scratch (brotli/byte-array-out-stream)
                     br      (brotli/compress-out-stream scratch :window-size context-window)]
           (loop [last-body-hash nil
                  seen-epoch     @(:biff.datastar/epoch req)]
             (let [render-start-ms (System/currentTimeMillis)
                   current-req     (current-request req)
                   response        (response-map (handler current-req))
                   body            (or (:body response) "")
                   body-hash       (hash body)
                   send?           (and (seq body) (not= body-hash last-body-hash))
                   _               (when send?
                                     (.write raw-out (brotli/compress-stream scratch br (patch-elements-event body)))
                                     (.flush raw-out))
                   seen-epoch      (wait-for-update! req seen-epoch (.toMillis heartbeat-interval))
                   elapsed-ms      (- (System/currentTimeMillis) render-start-ms)]
               (when (< elapsed-ms rate-limit-ms)
                 (Thread/sleep (- rate-limit-ms elapsed-ms)))
               (recur (if send? body-hash last-body-hash)
                      seen-epoch))))
         (catch Throwable t
           (if (or (instance? InterruptedException t)
                   (= "Pipe closed" (.getMessage t)))
             (log/debug t "Datastar SSE stream closed after the client disconnected.")
             (log/error t "Datastar SSE loop crashed"))))))))

(defn wrap-datastar
  "Ring middleware that attaches parsed Datastar signals and `:biff.datastar/tab-id`,
   then upgrades Datastar SSE GET requests
   into long-lived compressed event streams."
  ([handler]
   (wrap-datastar handler nil))
  ([handler params]
   (fn [req]
     (let [req (-> (merge req params)
                   attach-signals
                   attach-tab-id)]
       (if (datastar-sse-request? req)
         (do
           (when-not (and (:biff.datastar/lock req)
                          (:biff.datastar/condition req)
                          (:biff.datastar/epoch req))
             (throw (ex-info "Datastar SSE requests require the keys returned by new-lock in the request."
                             {})))
           {:status  200
            :headers {"Content-Type"     "text/event-stream; charset=utf-8"
                      "Cache-Control"    "no-store"
                      "Content-Encoding" "br"}
            :body    (stream-response-body handler req)})
         (response-map (handler req)))))))

(defn module
  []
  {:biff.ring/site-middleware [wrap-datastar]
   :biff.db/on-tx             #'refresh
   :biff.core/init            (fn [_] (new-lock))})
