(ns com.example
  (:require [com.biffweb :as biff]
            [com.example.feat.app :as app]
            [com.example.feat.auth :as auth]
            [com.example.feat.home :as home]
            [com.example.feat.worker :as worker]
            [com.example.schema :refer [malli-opts]]
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
              (map :routes features)]
             (map :api-routes features)])

(def handler (-> (biff/reitit-handler {:routes routes})
                 (biff/wrap-inner-defaults {})))

(defn on-tx [sys tx]
  (let [sys (biff/assoc-db sys)]
    (doseq [{:keys [on-tx]} features
            :when on-tx]
      (on-tx sys tx))))

(def tasks (->> features
                (mapcat :tasks)
                (map #(update % :task comp biff/assoc-db))))

(def static-pages (apply biff/safe-merge (map :static features)))

(defn generate-assets! [sys]
  (when (:example/enable-web sys)
    (biff/export-rum static-pages "target/resources/public")
    (biff/sh "bin/tailwindcss"
             "-i" "tailwind.css"
             "-o" "target/resources/public/css/main.css"
             "--minify")
    ;; todo delete files older than 10 seconds
    ))

(defn on-save [sys]
  (biff/eval-files! sys)
  (generate-assets! sys))

(defn start []
  (biff/start-system
    {:example/chat-clients (atom #{})
     :biff/after-refresh `start
     :biff/handler #'handler
     :biff/malli-opts #'malli-opts
     :biff.hawk/on-save #'on-save
     :biff.xtdb/on-tx #'on-tx
     :biff.chime/tasks tasks
     :biff/config "config.edn"
     :biff/components [biff/use-config
                       biff/use-xt
                       biff/use-tx-listener
                       (biff/use-when
                         :example/enable-web
                         biff/use-outer-default-middleware
                         biff/use-jetty)
                       biff/use-chime
                       (biff/use-when
                         :example/enable-hawk
                         biff/use-hawk)]})
  (generate-assets! @biff/system)
  (println "Go to" (:biff/base-url @biff/system)))

(defn -main [& args]
  (start)
  (apply nrepl-cmd/-main args))
