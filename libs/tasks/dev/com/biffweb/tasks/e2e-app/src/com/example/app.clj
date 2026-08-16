(ns com.example.app
  (:gen-class)
  (:import [com.sun.net.httpserver HttpHandler HttpServer]
           [java.net InetSocketAddress]
           [java.nio.charset StandardCharsets])
  (:require [nrepl.server :as nrepl]))

(def response (atom "ok"))

(def handler
  (reify HttpHandler
    (handle [_ exchange]
      (let [body (.getBytes @response StandardCharsets/UTF_8)]
        (.sendResponseHeaders exchange 200 (count body))
        (with-open [out (.getResponseBody exchange)]
          (.write out body))))))

(defn -main [& _]
  (let [port   (parse-long (or (System/getenv "PORT") "18080"))
        server (HttpServer/create (InetSocketAddress. port) 0)]
    (.createContext server "/" handler)
    (.start server)
    (nrepl/start-server :bind "localhost" :port 17888)
    (println "e2e-app-started")
    @(promise)))
