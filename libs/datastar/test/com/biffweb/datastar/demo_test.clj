(ns com.biffweb.datastar.demo-test
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [com.biffweb.datastar.demo :as demo]))

(defn- test-state []
  (atom demo/default-app-state))

(defn- request [state req]
  (demo/handler (assoc req ::demo/state state)))

(deftest demo-page-uses-published-datastar-bundle
  (let [state    (test-state)
        response (request state {:request-method :get
                                 :uri            "/"
                                 :headers        {}
                                 :query-params   {}})
        body     (:body response)]
    (is (= 200 (:status response)))
    (is (str/includes? body demo/datastar-script-url))
    (is (str/includes? body "data-signals:biff_datastar_tab-id__case.kebab"))
    (is (str/includes? body "data-bind=\"com_biffweb_datastar_demo_channel-id\""))
    (is (str/includes? body "data-signals__ifmissing="))
    (is (not (str/includes? body "com_biffweb_datastar_demo_message-count")))
    (is (str/includes? body "requestAnimationFrame(() =&gt; {"))
    (is (str/includes? body "data-on:submit=\"@post(el.dataset.action)\""))
    (is (str/includes? body "data-action=\"/messages\""))
    (is (= 1 (count (re-seq #"id=\"biff-datastar-content\"" body))))
    (is (not (str/includes? body "\"X-CSRF-Token\":")))
    (is (not (str/includes? body "{contentType: &apos;form&apos;")))))

(deftest missing-channel-page-does-not-create-a-channel
  (let [state    (test-state)
        response (request state {:request-method :get
                                 :uri            "/"
                                 :headers        {}
                                 :query-params   {"channel" "missing"}})
        body     (:body response)]
    (is (= 200 (:status response)))
    (is (str/includes? body "Channel not found"))
    (is (str/includes? body "#missing"))
    (is (str/includes? body "com_biffweb_datastar_demo_channel-id&quot;:&quot;missing"))
    (is (not (str/includes? body "@post(&quot;/messages&quot;)")))
    (is (nil? (get-in @state [:channels "missing"])))))

(deftest send-message-action-clears-message-signal
  (let [state    (test-state)
        response (#'demo/send-message-handler
                  {::demo/state           state
                   :biff.datastar/signals {::demo/channel-id   "general"
                                           ::demo/display-name "Alice"
                                           ::demo/message-text "hello"}})]
    (is (= 200 (:status response)))
    (is (= "text/event-stream; charset=utf-8"
           (get-in response [:headers "Content-Type"])))
    (is (str/includes? (:body response) "event: datastar-patch-signals"))
    (is (str/includes? (:body response)
                       (json/write-str {"com_biffweb_datastar_demo_message-text" ""})))
    (is (= 1 (count (get-in @state [:channels "general" :messages]))))))

(deftest send-message-action-uses-channel-signal
  (let [state    (atom (-> demo/default-app-state
                           (assoc-in [:channels "noice"]
                                     {:id       "noice"
                                      :name     "noice"
                                      :messages []})
                           (update :channel-order conj "noice")))
        response (#'demo/send-message-handler
                  {::demo/state           state
                   :biff.datastar/signals {::demo/channel-id   "noice"
                                           ::demo/display-name "Alice"
                                           ::demo/message-text "hello"}})]
    (is (= 200 (:status response)))
    (is (empty? (get-in @state [:channels "general" :messages])))
    (is (= ["hello"]
           (mapv :text (get-in @state [:channels "noice" :messages]))))))

(deftest set-channel-action-uses-empty-204-response
  (let [state    (atom (assoc-in demo/default-app-state
                                 [:tab-state "tab-1" :channel-id]
                                 "__new__"))
        response (#'demo/set-channel-handler
                  {::demo/state           state
                   :biff.datastar/tab-id  "tab-1"
                   :biff.datastar/signals {::demo/channel-id "general"}})]
    (is (= 204 (:status response)))
    (is (nil? (:body response)))
    (is (= "general"
           (get-in @state [:tab-state "tab-1" :channel-id])))))
