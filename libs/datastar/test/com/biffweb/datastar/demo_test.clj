(ns com.biffweb.datastar.demo-test
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [com.biffweb.datastar.demo :as demo]))

(deftest demo-page-uses-published-datastar-bundle
  (let [response (demo/app-sync {:request-method :get
                                 :uri            "/"
                                 :headers        {}
                                 :query-params   {}})
        body     (:body response)]
    (is (= 200 (:status response)))
    (is (str/includes? body demo/datastar-script-url))
    (is (str/includes? body "const methods = ['get', 'post', 'put', 'patch', 'delete'];"))
    (is (str/includes? body "'X-CSRF-Token': "))
    (is (str/includes? body "data-signals:tab-id"))
    (is (str/includes? body "data-bind:channelId"))
    (is (str/includes? body "data-signals:message-count"))
    (is (str/includes? body "requestAnimationFrame(() =&gt; { el.scrollTop = el.scrollHeight; })"))
    (is (str/includes? body "@post(&quot;/messages&quot;)"))
    (is (not (str/includes? body "\"X-CSRF-Token\":")))
    (is (not (str/includes? body "{contentType: &apos;form&apos;")))))

(deftest missing-channel-page-does-not-create-a-channel
  (let [previous-state @demo/app-state]
    (try
      (reset! demo/app-state {:channels      {"general" {:id       "general"
                                                         :name     "general"
                                                         :messages []}}
                              :channel-order ["general"]
                              :tab-state     {}})
      (let [response (demo/app-sync {:request-method :get
                                     :uri            "/"
                                     :headers        {}
                                     :query-params   {"channel" "missing"}})
            body     (:body response)]
        (is (= 200 (:status response)))
        (is (str/includes? body "Channel not found"))
        (is (str/includes? body "#missing"))
        (is (not (str/includes? body "@post(&quot;/messages&quot;)")))
        (is (nil? (get-in @demo/app-state [:channels "missing"]))))
      (finally
        (reset! demo/app-state previous-state)))))

(deftest send-message-action-clears-message-signal
  (let [previous-state @demo/app-state]
    (try
      (reset! demo/app-state {:channels      {"general" {:id       "general"
                                                         :name     "general"
                                                         :messages []}}
                              :channel-order ["general"]
                              :tab-state     {}})
      (let [response (#'demo/send-message-handler
                      {:biff.datastar/signals {:displayname "Alice"
                                               :channelid   "general"
                                               :messagetext "hello"}})]
        (is (= 200 (:status response)))
        (is (= "text/event-stream; charset=utf-8"
               (get-in response [:headers "Content-Type"])))
        (is (str/includes? (:body response) "event: datastar-patch-signals"))
        (is (str/includes? (:body response) (json/write-str {"messagetext" ""})))
        (is (= 1 (count (get-in @demo/app-state [:channels "general" :messages])))))
      (finally
        (reset! demo/app-state previous-state)))))

(deftest set-channel-action-uses-empty-204-response
  (let [previous-state @demo/app-state]
    (try
      (reset! demo/app-state {:channels      {"general" {:id       "general"
                                                         :name     "general"
                                                         :messages []}}
                              :channel-order ["general"]
                              :tab-state     {"tab-1" {:channel-id "__new__"}}})
      (let [response (#'demo/set-channel-handler
                      {:biff.datastar/tab-id  "tab-1"
                       :biff.datastar/signals {:channelid "general"}})]
        (is (= 204 (:status response)))
        (is (nil? (:body response)))
        (is (= "general"
               (get-in @demo/app-state [:tab-state "tab-1" :channel-id]))))
      (finally
        (reset! demo/app-state previous-state)))))
