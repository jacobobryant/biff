(ns com.example
  (:require [com.biffweb :as biff]
            [com.example.schema :refer [malli-opts]]
            [com.example.feat :as feat]
            [nrepl.cmdline :as nrepl-cmd]))

(def config (biff/read-config))

(def handler (-> (biff/reitit-handler {:routes feat/routes})
                 (biff/wrap-default-middleware config)))

(defn on-save [sys]
  (biff/eval-files! sys)
  (biff/export-rum feat/static-pages "target/resources/public"))

(defn start []
  (biff/start-system
    {:example/chat-clients (atom #{})
     :biff/after-refresh `start
     :biff/handler #'handler
     :biff/malli-opts #'malli-opts
     :biff.hawk/on-save #'on-save
     :biff/components [#(merge config %)
                       biff/use-xt
                       biff/use-wrap-env
                       biff/use-jetty
                       biff/use-hawk]})
  (println "Go to" (:biff/base-url config)))

(defn -main [& args]
  (start)
  (apply nrepl-cmd/-main args))

(comment
  (biff/refresh)
  )
