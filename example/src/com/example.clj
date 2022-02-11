(ns com.example
  (:require [com.biffweb :as biff]
            [com.example.feat.app :as app]
            [com.example.feat.auth :as auth]
            [com.example.feat.home :as home]
            [com.example.schema :refer [malli-opts]]
            [ring.middleware.anti-forgery :as anti-forgery]
            [nrepl.cmdline :as nrepl-cmd]))

(def features
  [app/features
   auth/features
   home/features])

(def routes [["" {:middleware [anti-forgery/wrap-anti-forgery]}
              (map :routes features)]
             (map :api-routes features)])

(def static-pages (apply biff/safe-merge (map :static features)))

(defn on-tx [sys tx]
  (doseq [{:keys [on-tx]} features
          :when on-tx]
    (on-tx sys tx)))

(def handler (-> (biff/reitit-handler {:routes routes})
                 (biff/wrap-inner-defaults {})))

(defn on-save [sys]
  (biff/eval-files! sys)
  (biff/export-rum static-pages "target/resources/public"))

(defn start []
  (biff/start-system
    {:example/chat-clients (atom #{})
     :biff/after-refresh `start
     :biff/handler #'handler
     :biff/malli-opts #'malli-opts
     :biff.hawk/on-save #'on-save
     :biff.xtdb/on-tx #'on-tx
     :biff/components [biff/use-config
                       biff/use-xt
                       biff/use-tx-listener
                       biff/use-outer-default-middleware
                       biff/use-jetty
                       biff/use-hawk]})
  (println "Go to" (:biff/base-url @biff/system)))

(defn -main [& args]
  (start)
  (apply nrepl-cmd/-main args))
