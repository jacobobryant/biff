(ns com.biffweb.impl.middleware-test
  (:require [clojure.test :refer [deftest is]]
            [cheshire.core :as cheshire]
            [com.biffweb :as biff]))

(def default-request
  {:request-method :get
   :uri "/"
   :scheme :https
   :headers {"host" "example.com"}
   :biff.middleware/cookie-secret (biff/generate-secret 16)})

(defn call-with-headers [handler ctx]
  (let [resp (handler (merge default-request ctx))]
    (cond-> resp
      (not (string? (:body resp))) (update :body slurp)
      true (dissoc :session))))

(defn string->stream
  ([s] (string->stream s "UTF-8"))
  ([s encoding]
   (-> s
       (.getBytes encoding)
       (java.io.ByteArrayInputStream.))))

(def param-handler
  (-> (fn [{:keys [params] :as ctx}]
        {:status 200
         :headers {"Content-Type" "text/plain"}
         :body (pr-str params)})
      biff/wrap-site-defaults
      biff/wrap-base-defaults))

(defn constant-handler [response]
  (-> (constantly response)
      biff/wrap-site-defaults
      biff/wrap-base-defaults))

(defn call
  ([handler ctx]
   (let [ctx (cond-> ctx
               (:body ctx) (update :body string->stream))
         resp (handler (merge default-request ctx))]
     (cond-> resp
       (not (string? (:body resp))) (update :body slurp)
       true (dissoc :session :headers))))
  ([ctx]
   (call param-handler ctx)))

(deftest middleware
  (is (= (update (call-with-headers param-handler {}) :headers dissoc "Set-Cookie")
         {:status 200,
          :headers
          {"Content-Type" "text/plain; charset=utf-8",
           "Content-Length" "2"
           "X-Frame-Options" "SAMEORIGIN",
           "X-Content-Type-Options" "nosniff",
           "Strict-Transport-Security" "max-age=31536000; includeSubDomains"},
          :body "{}"}))

  (is (= (call param-handler {:query-string "foo=bar"})
         {:status 200, :body "{:foo \"bar\"}"}))

  (is (= (call param-handler {:method :post
                              :headers {"content-type" "application/json"}
                              :body (cheshire/generate-string {:baz "quux"})})
         {:status 200, :body "{:baz \"quux\"}"}))

  (is (= (call param-handler {:method :post
                              :headers {"content-type" "application/x-www-form-urlencoded"}
                              :body "foo=bar"})
         {:status 200, :body "{:foo \"bar\"}"}))

  (is (= (call (constant-handler {:status 200
                                  :headers {"Content-Type" "application/edn"}
                                  :body (pr-str {:foo :bar})})
               {})
         {:status 200, :body "{:foo :bar}"}))

  (is (= (call (constant-handler {:status 200
                                  :body {:foo :bar}})
               {:headers {"accept" "application/edn"}})
         {:status 200, :body "{:foo :bar}"})))
