(ns com.example
  (:require [com.biffweb :as biff]
            [com.example.feat.app :as app]
            [com.example.feat.auth :as auth]
            [com.example.feat.home :as home]
            [com.example.feat.worker :as worker]
            [com.example.schema :refer [malli-opts]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :as test]
            [clojure.tools.logging :as log]
            [ring.middleware.anti-forgery :as anti-forgery]
            [nrepl.cmdline :as nrepl-cmd]))

(def features
  [app/features
   auth/features
   home/features
   worker/features])

(def routes [["" {:middleware [anti-forgery/wrap-anti-forgery
                               biff/wrap-anti-forgery-websockets
                               biff/wrap-render-rum]}
              (keep :routes features)]
             (keep :api-routes features)])

(def handler (-> (biff/reitit-handler {:routes routes})
                 (biff/wrap-inner-defaults {})))

(def static-pages (apply biff/safe-merge (map :static features)))

(defn generate-assets! [sys]
  (when (:com.example/enable-web sys)
    (biff/export-rum static-pages "target/resources/public")
    (biff/delete-old-files {:dir "target/resources/public"
                            :exts [".html"]})))

(defn on-save [sys]
  (biff/add-libs)
  (biff/eval-files! sys)
  (generate-assets! sys)
  (test/run-all-tests #"com.example.test.*"))

(defn start []
  (biff/start-system
   {:biff/features #'features
    :com.example/chat-clients (atom #{})
    :biff/after-refresh `start
    :biff/handler #'handler
    :biff/malli-opts #'malli-opts
    :biff.beholder/on-save #'on-save
    :biff/config "config.edn"
    :biff/components [biff/use-config
                      biff/use-random-default-secrets
                      biff/use-xt
                      biff/use-queues
                      biff/use-tx-listener
                      (biff/use-when
                       :com.example/enable-web
                       biff/use-outer-default-middleware
                       biff/use-jetty)
                      (biff/use-when
                       :com.example/enable-worker
                       biff/use-chime)
                      (biff/use-when
                       :com.example/enable-beholder
                       biff/use-beholder)]})
  (generate-assets! @biff/system)
  (log/info "Go to" (:biff/base-url @biff/system)))

(defn -main [& args]
  (start)
  (apply nrepl-cmd/-main args))
