(ns com.biffweb.stuff-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [com.biffweb.stuff :as stuff]
            [com.biffweb.stuff.bin :as stuff.bin]
            [com.biffweb.stuff.secret :as stuff.secret]))

(deftest wrap-params-normalizes-and-combines-params
  (let [request {:query-params {"query"  "query"
                                "shared" "query"}
                 :params       {'generic "generic"
                                "shared" "generic"}
                 :form-params  {"form" "form"}
                 :json-params  {"json" {"nested" true
                                        1        :ignored}}
                 :body         {"body" "body"}
                 :body-params  {:shared "body"
                                :final  "final"}}
        result  ((stuff/wrap-params identity) request)]
    (is (= {:query   "query"
            :generic "generic"
            :form    "form"
            :json    {:nested true}
            :body    "body"
            :shared  "generic"
            :final   "final"}
           (:biff.stuff/params result)))))

(deftest secret-delay-redacts-its-value
  (let [secret (stuff.secret/secret-delay "super-secret")]
    (is (= "#<SecretDelay: redacted>" (str secret)))
    (is (= "#<SecretDelay: redacted>" (pr-str secret)))
    (is (= "super-secret" (force secret)))
    (is (= "super-secret" (secret)))))

(deftest binaries-default-to-target-bin
  (let [seen (atom nil)]
    (with-redefs-fn {#'com.biffweb.stuff.bin/preferred-bin-path
                     (fn [executable-basename]
                       (reset! seen executable-basename)
                       "/usr/bin/example")

                     #'com.biffweb.stuff.bin/local-bin-installed?
                     (constantly false)

                     #'com.biffweb.stuff.bin/system-bin-path
                     (constantly "/usr/bin/example")}
      #(is (= "/usr/bin/example"
              (stuff.bin/ensure-binary
               {:executable-basename "example"
                :get-version         (constantly "1.0.0")
                :target-version      "1.0.0"
                :url                 "https://example.com/example"}))))
    (is (= "example" @seen))
    (is (= "target/bin" stuff.bin/bin-dir))))

(deftest unsupported-platform-requires-manual-install
  (let [ex (try
             (stuff.bin/check-platform {:supported-platforms #{[:linux :amd64]}
                                        :binary              "example"
                                        :version             "1.2.3"
                                        :os                  :windows
                                        :arch                :arm64})
             nil
             (catch UnsupportedOperationException e
               e))]
    (is (instance? UnsupportedOperationException ex))
    (is (= (str "Unsupported example binary platform: Unable to install "
                "example version 1.2.3 for your platform: "
                "{:os :windows, :arch :arm64}. You'll need to install it "
                "manually and ensure it's on the path.")
           (ex-message ex)))))

(deftest newly-installed-binary-must-have-target-version
  (let [installed-path "target/bin/example"]
    (with-redefs-fn {#'com.biffweb.stuff.bin/preferred-bin-path (constantly nil)

                     #'com.biffweb.stuff.bin/install-binary!
                     (constantly installed-path)}
      #(let [ex (try
                  (stuff.bin/ensure-binary
                   {:executable-basename "example"
                    :get-version         (constantly nil)
                    :target-version      "1.0.0"
                    :url                 "https://example.com/example"})
                  nil
                  (catch Exception e
                    e))]
         (is (instance? clojure.lang.ExceptionInfo ex))
         (is (= {:actual-version      nil
                 :executable-basename "example"
                 :expected-version    "1.0.0"
                 :path                installed-path}
                (ex-data ex)))))))

(deftest custom-installer-takes-precedence-over-url
  (let [calls (atom [])]
    (with-redefs-fn {#'com.biffweb.stuff.bin/preferred-bin-path
                     (constantly nil)

                     #'com.biffweb.stuff.bin/local-bin-installed?
                     (constantly true)

                     #'com.biffweb.stuff.bin/local-bin-path
                     (constantly "target/bin/example")

                     #'com.biffweb.stuff.bin/install-binary!
                     (fn [_] (swap! calls conj :url))}
      #(is (= "target/bin/example"
              (stuff.bin/ensure-binary
               {:executable-basename "example"
                :get-version         (constantly "1.0.0")
                :install             (fn []
                                       (swap! calls conj :install)
                                       "target/bin/example")
                :target-version      "1.0.0"
                :url                 "https://example.com/example"}))))
    (is (= [:install] @calls))))

(deftest download-times-out-on-stalled-response
  (let [server (com.sun.net.httpserver.HttpServer/create
                (java.net.InetSocketAddress. "127.0.0.1" 0)
                0)]
    (try
      (.createContext
       server
       "/binary"
       (reify com.sun.net.httpserver.HttpHandler
         (handle [_ exchange]
           (.sendResponseHeaders exchange 200 5)
           (Thread/sleep 150)
           (with-open [out (.getResponseBody exchange)]
             (.write out (.getBytes "hello"))))))
      (.start server)
      (let [dir  (.toFile (java.nio.file.Files/createTempDirectory
                           "biff-stuff-test"
                           (make-array
                            java.nio.file.attribute.FileAttribute 0)))
            url  (str "http://127.0.0.1:" (.getPort (.getAddress server)) "/binary")
            dest (io/file dir "binary")
            ex   (with-redefs [stuff.bin/download-connect-timeout-ms 50
                               stuff.bin/download-read-timeout-ms    50]
                   (try
                     (#'com.biffweb.stuff.bin/download-to! "binary" url dest)
                     nil
                     (catch Exception e
                       e)))]
        (is (instance? clojure.lang.ExceptionInfo ex))
        (is (= url (:url (ex-data ex))))
        (is (= 50 (:connect-timeout-ms (ex-data ex))))
        (is (= 50 (:read-timeout-ms (ex-data ex))))
        (io/delete-file dest true)
        (io/delete-file dir true))
      (finally
        (.stop server 0)))))
