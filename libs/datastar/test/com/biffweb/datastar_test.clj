(ns com.biffweb.datastar-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer [deftest is testing]]
            [com.biffweb.datastar :as datastar]
            [ring.core.protocols :as rp])
  (:import (java.util.concurrent.locks Condition ReentrantLock)))

(deftest signal-name-test
  (is (= "plain" (datastar/signal-name :plain)))
  (is (= "account_profile_name"
         (datastar/signal-name :account.profile/name)))
  (is (= "account_profile_name.first.2"
         (datastar/signal-name [:account.profile/name :first 2])))
  (is (thrown-with-msg? AssertionError
                        #"Underscores are not allowed"
                        (datastar/signal-name :invalid_name)))
  (is (thrown-with-msg? AssertionError
                        #"Periods are not allowed"
                        (datastar/signal-name :account/invalid.name))))

(deftest signals-json-test
  (let [encoded (datastar/signals-json
                 {:account.profile/name "Ada"
                  :nested               {:enabled true}
                  "string-key"          3})]
    (is (= {"account_profile_name" "Ada"
            "nested"               {"enabled" true}
            "string-key"           3}
           (json/read-str encoded)))))

(deftest wrap-signals-test
  (let [client-tab-id (random-uuid)
        user-id       (random-uuid)
        handler       identity
        wrapped       (datastar/wrap-signals handler)]
    (testing "ignores ordinary requests"
      (is (= {:request-method :get :headers {}}
             (wrapped {:request-method :get :headers {}}))))
    (testing "parses GET signals and promotes special signals"
      (let [request (wrapped
                     {:request-method :get
                      :headers        {"datastar-request" "true"}
                      :session        {:uid user-id}

                      :query-params {"datastar"
                                     (datastar/signals-json
                                      {:biff.datastar/client-tab-id
                                       client-tab-id

                                       :biff.datastar/anti-forgery-token
                                       "token"

                                       :profile/display-name "Ada"})}})]
        (is (uuid? (:biff.datastar/tab-id request)))
        (is (= 5 (.version (:biff.datastar/tab-id request))))
        (is (not= client-tab-id (:biff.datastar/tab-id request)))
        (is (= (str client-tab-id)
               (:biff.datastar/client-tab-id
                (:biff.datastar/signals request))))
        (is (= "token" (:biff.datastar/anti-forgery-token
                        (:biff.datastar/signals request))))
        (is (= "Ada" (:profile/display-name
                      (:biff.datastar/signals request))))
        (is (= "token" (get-in request [:headers "x-csrf-token"])))))
    (testing "parses DELETE signals from query parameters"
      (let [request (wrapped
                     {:request-method :delete
                      :headers        {"datastar-request" "true"}
                      :query-params   {"datastar"
                                       (datastar/signals-json
                                        {:profile/display-name "Grace"})}})]
        (is (= {:profile/display-name "Grace"}
               (:biff.datastar/signals request)))))
    (testing "parses non-GET body parameters recursively"
      (let [request (wrapped
                     {:request-method :post
                      :headers        {"datastar-request" "true"}

                      :body-params
                      {"profile_display-name" "Grace"
                       "nested"               {"account_active" true}}})]
        (is (= {:profile/display-name "Grace"
                :nested               {:account/active true}}
               (:biff.datastar/signals request)))))))

(deftest scoped-tab-id-test
  (let [wrapped       (datastar/wrap-signals identity)
        client-tab-id (random-uuid)
        user-id       (random-uuid)
        request       (fn [scope-id tab-id]
                        (wrapped
                         {:request-method :post
                          :headers        {"datastar-request" "true"}
                          :session        {:uid scope-id}

                          :body-params
                          {:biff_datastar_client-tab-id tab-id}}))
        tab-id        (:biff.datastar/tab-id
                       (request user-id client-tab-id))]
    (is (= tab-id (:biff.datastar/tab-id
                   (request user-id (str client-tab-id)))))
    (is (not= tab-id (:biff.datastar/tab-id
                      (request (random-uuid) client-tab-id))))
    (is (nil? (:biff.datastar/tab-id (request nil client-tab-id))))
    (is (nil? (:biff.datastar/tab-id
               (request user-id (apply str (repeat 1025 "x"))))))
    (is (= tab-id
           (:biff.datastar/tab-id
            (wrapped
             {:request-method            :post
              :headers                   {"datastar-request" "true"}
              :biff.datastar/get-user-id (constantly (str user-id))

              :body-params
              {:biff_datastar_client-tab-id client-tab-id
               :biff_datastar_tab-id        (random-uuid)}}))))))

(deftest init-opts-test
  (let [opts (datastar/init-opts)]
    (is (= "self.crypto.randomUUID()"
           (:data-signals:biff_datastar_client-tab-id__case.kebab opts)))
    (is (= (:data-init opts) (:data-on:online__window opts)))
    (is (re-find #"biff-datastar-sse=true" (:data-init opts))))
  (is (= {"biff_datastar_anti-forgery-token" "csrf"}
         (-> (datastar/init-opts {:anti-forgery-token "csrf"})
             :data-signals
             json/read-str))))

(deftest patch-signals-test
  (is (= {:status  200
          :headers {"Cache-Control" "no-store"
                    "Content-Type"  "text/event-stream; charset=utf-8"}
          :body    (str "event: datastar-patch-signals\n"
                        "data: signals {\"counter_value\":2}\n\n")}
         (datastar/patch-signals {:counter/value 2}))))

(deftest lock-and-refresh-test
  (let [{:biff.datastar/keys [lock condition epoch] :as state}
        (datastar/new-lock)]
    (is (instance? ReentrantLock lock))
    (is (instance? Condition condition))
    (is (= 0 @epoch))
    (is (nil? (datastar/refresh state)))
    (is (= 1 @epoch))))

(deftest wrap-sse-render-test
  (let [seen    (atom nil)
        handler (fn [request]
                  (reset! seen request)
                  {:status 200 :body "<div id=\"content\">ok</div>"})
        wrapped (datastar/wrap-sse-render handler)]
    (testing "ordinary requests pass through with SSE metadata"
      (is (= {:status 200 :body "<div id=\"content\">ok</div>"}
             (wrapped {:request-method :get :headers {} :query-params {}})))
      (is (false? (:biff.datastar/sse-request @seen))))
    (testing "SSE requests return a Brotli stream response"
      (let [response (wrapped
                      (merge (datastar/new-lock)
                             {:request-method :get
                              :headers        {}
                              :query-params   {"biff-datastar-sse" "true"}}))]
        (is (= 200 (:status response)))
        (is (= {"Content-Type"     "text/event-stream; charset=utf-8"
                "Cache-Control"    "no-store"
                "Content-Encoding" "br"}
               (:headers response)))
        (is (satisfies? rp/StreamableResponseBody (:body response)))))))

(deftest module-test
  (let [module (datastar/module)
        state  ((:biff.core/init module) nil)]
    (is (= 1 (count (:biff.ring/site-middleware module))))
    (is (fn? (first (:biff.ring/site-middleware module))))
    (is (var? (:biff.core/on-tx module)))
    (is (instance? ReentrantLock (:biff.datastar/lock state)))
    (is (instance? clojure.lang.IAtom (:biff.datastar/epoch state)))
    ((:biff.core/on-tx module) state)
    (is (= 1 @(:biff.datastar/epoch state)))))
