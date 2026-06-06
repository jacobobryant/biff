(ns com.biffweb.datastar-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [com.biffweb.datastar :as datastar]
   [com.biffweb.datastar.impl.brotli :as brotli]
   [ring.core.protocols :as rp])
  (:import
   (java.io PipedInputStream PipedOutputStream)))

(defn- wait-for [pred]
  (loop [remaining 100]
    (if (or (pred) (zero? remaining))
      (pred)
      (do
        (Thread/sleep 20)
        (recur (dec remaining))))))

(deftest init-opts-include-page-request-header
  (let [opts datastar/init-opts]
    (is (= datastar/tab-id-js (:data-signals:tab-id opts)))
    (is (str/includes? (:data-init opts) "@get("))
    (is (str/includes? (:data-init opts) "'X-Biff-Datastar-Page-Request': 'true'"))
    (is (not (str/includes? (:data-init opts) "'X-CSRF-Token': \"csrf-token\"")))))

(deftest configure-csrf-wraps-actions
  (let [script         (datastar/configure-csrf "https://example.test/datastar.js" "csrf-token")
        default-script (datastar/configure-csrf "csrf-token")]
    (is (str/includes? script "import { action, actions } from \"https://example.test/datastar.js\";"))
    (is (str/includes? script "const methods = ['get', 'post', 'put', 'patch', 'delete'];"))
    (is (str/includes? script "headers: {"))
    (is (str/includes? script "'X-CSRF-Token': \"csrf-token\""))
    (is (str/includes? script "...(options.headers ?? {})"))
    (is (str/includes? default-script "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.1/bundles/datastar.js"))))

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
                            :body-params    {:tabId "tab-1"}})]
    (handler request)
    (is (= "tab-1" @seen-tab-id))))

(deftest wrap-datastar-normalizes-signal-keys
  (let [handler  (datastar/wrap-datastar
                  (fn [req]
                    {:status 200
                     :body   (:biff.datastar/signals req)}))
        response (handler {:request-method :post
                           :headers        {"datastar-request" "true"}
                           :body-params    {"displayname" "Alice"
                                            :messageText  "hello"}})]
    (is (= {:displayname "Alice"
            :messageText "hello"}
           (:body response)))))

(deftest sse-response-streams-initial-patch
  (let [handler  (datastar/wrap-datastar
                  (fn [req]
                    {:status 200
                     :body   (str "<div id=\"biff-datastar-content\">Hello from "
                                  (:biff.datastar/tab-id req)
                                  "</div>")}))
        request  (merge (datastar/new-lock)
                        {:request-method :get
                         :headers        {"x-biff-datastar-page-request" "true"
                                          "accept"                       "text/event-stream"
                                          "datastar-request"             "true"}
                         :params         {"datastar" "{\"tabId\":\"tab-1\"}"}})
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
          (is (str/includes? (brotli/decompress-stream buf) "Hello from tab-1")))
        (finally
          (datastar/refresh request)
          (.close in)
          (.close out)
          (future-cancel writer))))))
