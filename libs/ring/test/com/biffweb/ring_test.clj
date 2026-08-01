(ns com.biffweb.ring-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [com.biffweb.ring :as ring]
            [ring.util.codec :as codec])
  (:import [java.util UUID]))

(ring/defpath post-path ["/posts/:id" {:get identity}])

(ring/defroute greeting-route "/greeting/:name"
  :get
  (fn [{:keys [path-params]}]
    [:h1 "Hello " (:name path-params)]))

(ring/defroute effect-route
  [:test/value]

  :post
  (fn [_ value]
    {:status 201
     :body   [:span value]}))

(deftest path-renders-path-params
  (is (= "/posts/42" (ring/path "/posts/:id" 42)))
  (is (= "/posts/42" (ring/path ["/posts/:id" {:get identity}] 42)))
  (is (= "/posts/42" (post-path 42))))

(deftest path-round-trips-uuid-params
  (let [id      (UUID/fromString "1f936a6e-63cd-4ba1-8e21-566171a8233c")
        encoded (subs (ring/path "/items/:id" id) 7)
        seen    ((ring/wrap-path-param-uuids :path-params)
                 {:path-params {:id    encoded
                                :slug  "not-a-uuid"
                                :other "......................"}})]
    (is (= 22 (count encoded)))
    (is (= {:id    id
            :slug  "not-a-uuid"
            :other "......................"}
           seen))))

(deftest path-encodes-query-params
  (let [url  (ring/path "/search" {:term "two words" :page 2})
        npy  (get (codec/form-decode (subs url (inc (.indexOf url "?"))))
                  "npy")
        seen ((ring/wrap-nippy-params :params)
              {:params {"npy" npy}})]
    (is (str/starts-with? url "/search?npy="))
    (is (= {"npy" npy :term "two words" :page 2} seen))))

(deftest path-testing-mode-returns-unencoded-params
  (binding [ring/*testing* true]
    (is (= ["/posts/7" {:tab "activity"}]
           (ring/path "/posts/:id" 7 {:tab "activity"})))))

(deftest path-rejects-invalid-input
  (doseq [[input args] [["/posts/:id" []]
                        ["/posts/:id" [1 {} :extra]]
                        ["/posts" [1 {}]]]]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Wrong number of args"
                          (apply ring/path input args))))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"reitit-style route vector"
                        (ring/path {:path "/posts"}))))

(deftest cross-origin-protection-allows-safe-methods
  (let [handler (ring/wrap-csrf-protection
                 (fn [_] {:status 204}))]
    (doseq [method [:get :head :options]]
      (is (= 204 (:status
                  (handler {:request-method method
                            :headers        {"sec-fetch-site" "cross-site"}})))))))

(deftest cross-origin-protection-checks-fetch-metadata
  (let [handler (ring/wrap-csrf-protection
                 (fn [_] {:status 204}))]
    (doseq [fetch-site ["same-origin" "none"]]
      (is (= 204 (:status
                  (handler {:request-method :post
                            :headers        {"sec-fetch-site" fetch-site}})))))
    (doseq [fetch-site ["same-site" "cross-site"]]
      (is (= 403 (:status
                  (handler {:request-method :post
                            :headers        {"sec-fetch-site" fetch-site}})))))))

(deftest cross-origin-protection-falls-back-to-origin
  (let [handler (ring/wrap-csrf-protection
                 (fn [_] {:status 204}))]
    (is (= 403 (:status (handler {:request-method :post :headers {}}))))
    (is (= 403 (:status
                (handler {:request-method :post
                          :headers        {"origin" "" "sec-fetch-site" ""}}))))
    (is (= 204 (:status
                (handler {:request-method :post
                          :headers        {"host"   "example.com:8443"
                                           "origin" "https://example.com:8443"}}))))
    (is (= 403 (:status
                (handler {:request-method :post
                          :headers        {"host"           "example.com:8443"
                                           "origin"         "https://example.com:8443"
                                           "sec-fetch-site" " "}}))))
    (doseq [origin ["https://other.example"
                    "https://example.com"
                    "not an origin"]]
      (is (= 403 (:status
                  (handler {:request-method :post
                            :headers        {"host"   "example.com:8443"
                                             "origin" origin}})))))))

(deftest cross-origin-protection-checks-websocket-handshakes
  (let [websocket    {:request-method :get
                      :headers        {"connection" "Upgrade"
                                       "upgrade"    "websocket"}}
        csrf-handler (ring/wrap-csrf-protection
                      (fn [_] {:status 204}))]
    (is (= 403 (:status (csrf-handler websocket))))
    (is (= 403 (:status
                (csrf-handler
                 (assoc-in websocket [:headers "sec-fetch-site"]
                           "cross-site")))))
    (is (= 204 (:status
                (csrf-handler
                 (assoc-in websocket [:headers "sec-fetch-site"]
                           "same-origin")))))
    (is (= 204 (:status
                (csrf-handler
                 (assoc websocket :headers
                        {"connection" "Upgrade"
                         "upgrade"    "websocket"
                         "host"       "example.com"
                         "origin"     "https://example.com"})))))))

(deftest path-param-middleware-decodes-only-encoded-uuids
  (let [id      (random-uuid)
        encoded (subs (ring/path "/:id" id) 1)
        handler (ring/wrap-path-param-uuids :path-params)]
    (is (= {:id id :slug "hello"}
           (handler {:path-params {:id encoded :slug "hello"}})))
    (is (nil? (handler {})))))

(deftest nippy-param-middleware-ignores-invalid-values
  (let [handler (ring/wrap-nippy-params :params)]
    (is (= {:npy "invalid" :existing true}
           (handler {:params {:npy "invalid" :existing true}})))
    (is (= {:existing true}
           (handler {:params {:existing true}})))))

(deftest resource-middleware-serves-files-and-indexes
  (let [handler (ring/wrap-resource (constantly {:status 404}))
        request {:request-method :get :uri "/css/test.css"}]
    (is (= 200 (:status (handler request))))
    (is (str/includes? (get-in (handler request)
                               [:headers "Content-Type"])
                       "text/css"))
    (is (= 404 (:status (handler {:request-method :get
                                  :uri            "/missing.txt"}))))
    (is (= 200 (:status
                (handler {:request-method        :get
                          :uri                   "/css"
                          :biff.ring/index-files ["test.css"]}))))))

(deftest internal-error-middleware-converts-exceptions
  (let [ex      (ex-info "boom" {:value 1})
        handler (ring/wrap-internal-error (fn [_] (throw ex)))]
    (is (= 500 (:status (handler {}))))
    (is (= {:status 500 :exception ex}
           ((ring/wrap-internal-error (fn [_] (throw ex)))
            {:biff.ring/on-error
             (fn [{:keys [status ex]}]
               {:status status :exception ex})})))))

(deftest ssl-middleware-honors-request-options
  (let [handler (ring/wrap-ssl (constantly {:status 200}))]
    (is (= 200 (:status (handler {:scheme           :http
                                  :server-name      "example.com"
                                  :server-port      80
                                  :uri              "/"
                                  :biff.ring/secure false}))))
    (is (= 307 (:status (handler {:scheme                 :http
                                  :server-name            "example.com"
                                  :server-port            80
                                  :uri                    "/"
                                  :biff.ring/ssl-redirect true}))))
    (is (= "max-age=31536000; includeSubDomains"
           (get-in (handler {:scheme         :https
                             :biff.ring/hsts true})
                   [:headers "Strict-Transport-Security"])))))

(deftest route-definitions-produce-reitit-routes
  (is (= "/greeting/:name" (first greeting-route)))
  (is (= ::greeting-route (get-in greeting-route [1 :name])))
  (is (fn? (get-in greeting-route [1 :get])))
  (is (= "/_biff/api/com.biffweb.ring-test/effect-route"
         (first effect-route)))
  (is (= #{:name :post} (set (keys (second effect-route)))))
  (is (= {:status  200
          :headers {"content-type" "text/html; charset=utf-8"}
          :body    "<!DOCTYPE html><h1>Hello Ada</h1>"}
         ((get-in greeting-route [1 :get])
          {:request-method :get
           :path-params    {:name "Ada"}})))
  (is (= {:status  201
          :headers {"content-type" "text/html; charset=utf-8"}
          :body    "<!DOCTYPE html><span>loaded</span>"}
         ((get-in effect-route [1 :post])
          {:request-method :post
           :biff.fx/handlers
           {:test/value (constantly "loaded")}}))))

(deftest make-handler-routes-site-and-api-requests
  (let [handler (ring/make-handler
                 {:site-routes [["/site" {:get (constantly {:status 200
                                                            :body   "site"})}]]
                  :api-routes  [["/api" {:get (constantly {:status 200
                                                           :body   "api"})}]]})]
    (is (= "site" (:body (handler {:request-method :get
                                   :uri            "/site"
                                   :scheme         :https}))))
    (is (= "api" (:body (handler {:request-method :get
                                  :uri            "/api"
                                  :scheme         :https}))))
    (is (= 404 (:status (handler {:request-method :get
                                  :uri            "/missing"
                                  :scheme         :https}))))))

(deftest module-collects-routes-and-middleware
  (let [modules (atom [{:biff.ring/routes
                        ["/module" {:get (constantly {:status 200
                                                      :body   "module"})}]}
                       {:biff.ring/base-middleware
                        [(fn [handler]
                           (fn [request]
                             (handler (assoc request :wrapped true))))]}])
        system  ((:biff.core/init (ring/module)) modules)
        handler (:biff.ring/handler system)]
    (is (some? (:biff.ring/fallback-session-store system)))
    (is (= "module" (:body (handler {:request-method :get
                                     :uri            "/module"
                                     :scheme         :https}))))))

(deftest server-module-handler-reflects-module-changes
  (let [modules (atom [])
        handler (:biff.ring/handler
                 ((:biff.core/init (ring/module)) modules))]
    (is (= 404 (:status (handler {:request-method :get
                                  :uri            "/later"
                                  :scheme         :https}))))
    (reset! modules [{:biff.ring/api-routes
                      ["/later" {:get (constantly {:status 204})}]}])
    (is (= 204 (:status (handler {:request-method :get
                                  :uri            "/later"
                                  :scheme         :https}))))))
