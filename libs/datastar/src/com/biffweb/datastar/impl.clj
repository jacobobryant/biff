(ns com.biffweb.datastar.impl
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [clojure.walk :as walk]
   [com.biffweb.core :as biff.core]
   [ring.core.protocols :as rp])
  (:import
   (com.aayushatharva.brotli4j Brotli4jLoader)
   (com.aayushatharva.brotli4j.encoder BrotliOutputStream Encoder$Mode Encoder$Parameters)
   (java.io ByteArrayOutputStream IOException)
   (java.io ByteArrayOutputStream)
   (java.util.concurrent.locks Condition ReentrantLock)))

(defrecord ^:private StreamingResponseBody [write-body]
  rp/StreamableResponseBody
  (write-body-to-stream [_ _ output-stream]
    (write-body output-stream)))

(def ^:private default-options
  {:biff.datastar/rate-limit-ms 15
   ;; copied from Hyperlith
   :biff.datastar/window-size 18
   :biff.datastar/quality 5
   :biff.datastar/buffer-size 16384})

;;;; Brotli ====================================================================

#_:clj-kondo/ignore
(defonce ^:private ensure-brotli
  (Brotli4jLoader/ensureAvailability))

(defn new-brotli-stream ^BrotliOutputStream
  [^ByteArrayOutputStream compressed-buffer
   {:biff.datastar/keys [buffer-size quality window-size]}]
  (BrotliOutputStream. compressed-buffer
                       (doto (Encoder$Parameters.)
                         (.setMode Encoder$Mode/TEXT)
                         (.setWindow window-size)
                         (.setQuality quality))
                       buffer-size))

(defn compress-chunk
  [^ByteArrayOutputStream compressed-buffer
   ^BrotliOutputStream brotli-stream
   text]
  (doto brotli-stream
    (.write (.getBytes ^String text "UTF-8"))
    (.flush))
  (let [compressed-bytes (.toByteArray compressed-buffer)]
    (.reset compressed-buffer)
    compressed-bytes))

;;;; Page init =================================================================

(def ^:private open-sse-action
  (str "@get("
       "window.location.pathname + "
       "(window.location.search + '&biff-datastar-sse=true').replace(/^&/,'?'), "
       "{openWhenHidden: false, retryMaxCount: Infinity})"))

(def init-opts
  {:data-signals:biff_datastar_tab-id__case.kebab
   "self.crypto.randomUUID().substring(0,8)"

   :data-init              open-sse-action
   :data-on:online__window open-sse-action})

;;;; Signal parsing ============================================================

(defn- parse-signal-str [s]
  (let [segments (str/split s #"_")]
    (keyword (str/join "." (subvec segments 0 (dec (count segments))))
             (peek segments))))

(defn- parse-signals [{:keys [headers
                              request-method
                              query-params
                              body-params
                              body
                              params]}]
  (cond
    (not= "true" (get headers "datastar-request"))
    nil

    (= request-method :get)
    (some-> (get query-params "datastar")
            (json/read-str :key-fn parse-signal-str))

    :else
    (walk/postwalk #(cond-> %
                      (map? %)
                      (update-keys (comp parse-signal-str name)))
                   (first (filterv map? [body-params body params])))))

(defn- merge-datastar-context [request]
  (let [signals (parse-signals request)]
    (into request
          (filter (comp some? val))
          {:biff.datastar/signals signals
           :biff.datastar/tab-id  (:biff.datastar/tab-id signals)

           :biff.datastar/sse-request
           (= (get-in request [:query-params "biff-datastar-sse"]) "true")})))

(defn- key-json [k]
  (if (keyword? k)
    (-> (subs (str k) 1)
        (str/replace #"[./]" "_"))
    (str k)))

(defn signals-json [signals]
  (json/write-str signals :key-fn key-json))

;;;; SSE stuff =================================================================

;; Called on system startup
(defn new-lock []
  (let [lock (ReentrantLock.)]
    {:biff.datastar/lock      lock
     :biff.datastar/condition (.newCondition lock)
     :biff.datastar/epoch     (atom 0)}))

;; Called by threads that update the DB
(defn refresh [{:biff.datastar/keys [lock condition epoch] :as ctx}]
  (biff.core/validate ctx {:required [:biff.datastar/lock
                                      :biff.datastar/condition
                                      :biff.datastar/epoch]})
  (.lock ^ReentrantLock lock)
  (try
    (swap! epoch inc)
    (.signalAll ^Condition condition)
    nil
    (finally
      (.unlock ^ReentrantLock lock))))

;; Used by the SSE handler to be notified when `refresh` is called
(defn- wait-for-refresh [{:biff.datastar/keys [lock condition epoch]}
                         observed-epoch]
  (.lock ^ReentrantLock lock)
  (try
    (while (= @epoch observed-epoch)
      (.await ^Condition condition))
    @epoch
    (finally
      (.unlock ^ReentrantLock lock))))

(defn- patch-elements-event [html]
  (str "event: datastar-patch-elements\n"
       "id: " (Integer/toHexString (hash html)) "\n"
       "data: elements " (str/replace html "\n" "\ndata: elements ")
       "\n\n"))

(defn- streaming-response-body [handler request]
  (let [{:biff.datastar/keys [rate-limit-ms epoch] :as opts}
        (merge default-options request)]
    (->StreamingResponseBody
     (fn [response-output]
       (try
         (with-open [compressed-buffer (ByteArrayOutputStream.)
                     brotli-stream     (new-brotli-stream compressed-buffer
                                                          opts)]
           (loop [previous-body-hash nil
                  observed-epoch     @epoch]
             (let [iteration-start-ms (System/currentTimeMillis)
                   response           (handler request)
                   body               (when (= (:status response) 200)
                                        (or (:body response) ""))
                   body-hash          (hash body)
                   body-changed?      (not= body-hash previous-body-hash)

                   _
                   (when body-changed?
                     (->> (patch-elements-event body)
                          (compress-chunk compressed-buffer brotli-stream)
                          (.write response-output))
                     (.flush response-output))

                   observed-epoch
                   (wait-for-refresh request observed-epoch)

                   elapsed-ms     (- (System/currentTimeMillis)
                                     iteration-start-ms)]
               (when (< elapsed-ms rate-limit-ms)
                 (Thread/sleep (- rate-limit-ms elapsed-ms)))
               (recur body-hash observed-epoch))))
         ;; Probably server shutdown
         (catch InterruptedException _e)
         ;; Probably client closed the connection
         (catch IOException _e)
         (catch Exception e (log/error e)))))))

(defn wrap-datastar
  [handler]
  (fn [request]
    (let [request (merge-datastar-context request)]
      (if (:biff.datastar/sse-request request)
        (do
          (biff.core/validate request {:required [:biff.datastar/lock
                                                  :biff.datastar/condition
                                                  :biff.datastar/epoch]})
          {:status  200
           :headers {"Content-Type"     "text/event-stream; charset=utf-8"
                     "Cache-Control"    "no-store"
                     "Content-Encoding" "br"}
           :body    (streaming-response-body handler request)})
        (handler request)))))

(defn module
  []
  {:biff.ring/site-middleware [wrap-datastar]
   :biff.core/on-tx           #'refresh
   :biff.core/init            (fn [_] (new-lock))})
