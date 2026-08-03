(ns com.biffweb.datastar-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [com.biffweb.datastar :as datastar]
   [ring.core.protocols :as rp])
  (:import
   (com.aayushatharva.brotli4j.decoder BrotliInputStream)
   (java.io ByteArrayOutputStream IOException PipedInputStream PipedOutputStream)))

(defn- decompress-stream [data]
  (with-open [in  (-> data io/input-stream BrotliInputStream.)
              out (ByteArrayOutputStream.)]
    (.enableEagerOutput in)
    (try
      (loop [read (.read in)]
        (when (<= 0 read)
          (.write out read)
          (recur (.read in))))
      (catch IOException _))
    (str out)))

(defn- wait-for [pred]
  (loop [remaining 100]
    (if (or (pred) (zero? remaining))
      (pred)
      (do
        (Thread/sleep 20)
        (recur (dec remaining))))))

(deftest init-opts-open-sse-request
  (let [opts datastar/init-opts]
    (is (= "self.crypto.randomUUID().substring(0,8)"
           (:data-signals:biff_datastar_tab-id__case.kebab opts)))
    (is (str/includes? (:data-init opts) "@get("))
    (is (str/includes? (:data-init opts) "biff-datastar-sse=true"))))

(deftest refresh-bumps-epoch
  (let [lock-state (datastar/new-lock)]
    (is (= 0 @(:biff.datastar/epoch lock-state)))
    (datastar/refresh lock-state)
    (is (= 1 @(:biff.datastar/epoch lock-state)))))

(deftest wrap-datastar-attaches-tab-id
  (let [seen-tab-id (atom nil)
        handler     (datastar/wrap-datastar
                     (fn [req]
                       (reset! seen-tab-id (:biff.datastar/tab-id req))
                       {:status 204}))
        request     (merge (datastar/new-lock)
                           {:request-method :post
                            :headers        {"datastar-request" "true"}
                            :body-params    {:biff_datastar_tab-id "tab-1"}})]
    (handler request)
    (is (= "tab-1" @seen-tab-id))))

(deftest wrap-datastar-normalizes-signal-keys
  (let [handler  (datastar/wrap-datastar
                  (fn [req]
                    {:status 200
                     :body   (:biff.datastar/signals req)}))
        response (handler {:request-method :post
                           :headers        {"datastar-request" "true"}
                           :body-params    {"user_display-name" "Alice"
                                            :message_text       "hello"}})]
    (is (= {:user/display-name "Alice"
            :message/text      "hello"}
           (:body response)))))

(deftest wrap-datastar-preserves-unnamespaced-signal-keys
  (let [handler  (datastar/wrap-datastar
                  (fn [req]
                    {:status 200
                     :body   (:biff.datastar/signals req)}))
        response (handler {:request-method :post
                           :headers        {"datastar-request" "true"}
                           :body-params    {"counter" 1}})]
    (is (= {:counter 1} (:body response)))))

(deftest nested-signal-names
  (is (= "foo_bar_baz.0.hello"
         (datastar/signal-name [:foo.bar/baz 0 :hello]))))

(deftest sse-response-streams-initial-patch
  (let [handler  (datastar/wrap-datastar
                  (fn [req]
                    {:status 200
                     :body   (str "<div id=\"biff-datastar-content\">Hello from "
                                  (:biff.datastar/tab-id req)
                                  "</div>")}))
        request  (merge (datastar/new-lock)
                        {:request-method :get
                         :headers        {"accept"           "text/event-stream"
                                          "datastar-request" "true"}
                         :query-params   {"biff-datastar-sse" "true"
                                          "datastar"          "{\"biff_datastar_tab-id\":\"tab-1\"}"}})
        response (handler request)
        body     (:body response)]
    (is (= 200 (:status response)))
    (is (= "br" (get-in response [:headers "Content-Encoding"])))
    (let [out    (PipedOutputStream.)
          in     (PipedInputStream. out 65536)
          writer (future (rp/write-body-to-stream body nil out))]
      (try
        (is (wait-for #(pos? (.available in))))
        (let [buf (byte-array (.available in))]
          (.read in buf)
          (is (str/includes? (decompress-stream buf) "Hello from tab-1")))
        (finally
          (datastar/refresh request)
          (.close in)
          (.close out)
          (future-cancel writer))))))
