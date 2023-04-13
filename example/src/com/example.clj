(ns com.example
  (:require [com.biffweb :as biff]
            [com.example.email :as email]
            [com.example.feat.app :as app]
            [com.example.feat.home :as home]
            [com.example.feat.worker :as worker]
            [com.example.schema :as schema]
            [clojure.test :as test]
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.repl :as tn-repl]
            [malli.core :as malc]
            [malli.registry :as malr]
            [nrepl.cmdline :as nrepl-cmd]))

(def features
  [app/features
   (biff/authentication-plugin {})
   home/features
   schema/features
   worker/features])

(def routes [["" {:middleware [biff/wrap-site-defaults]}
              (keep :routes features)]
             ["" {:middleware [biff/wrap-api-defaults]}
              (keep :api-routes features)]])

(def handler (-> (biff/reitit-handler {:routes routes})
                 biff/wrap-base-defaults))

(def static-pages (apply biff/safe-merge (map :static features)))

(defn generate-assets! [sys]
  (biff/export-rum static-pages "target/resources/public")
  (biff/delete-old-files {:dir "target/resources/public"
                          :exts [".html"]}))

(defn on-save [sys]
  (biff/add-libs)
  (biff/eval-files! sys)
  (generate-assets! sys)
  (test/run-all-tests #"com.example.test.*"))

(def malli-opts
  {:registry (malr/composite-registry
              malc/default-registry
              (apply biff/safe-merge
                     (keep :schema features)))})

(def components
  [biff/use-config
   biff/use-secrets
   biff/use-xt
   biff/use-queues
   biff/use-tx-listener
   biff/use-wrap-ctx
   biff/use-jetty
   biff/use-chime
   (biff/use-when
    :com.example/enable-beholder
    biff/use-beholder)])

(def initial-system
  {:com.example/chat-clients (atom #{})
   :biff/send-email #'email/send-email
   :biff/features #'features
   :biff/handler #'handler
   :biff/malli-opts #'malli-opts
   :biff.beholder/on-save #'on-save
   :biff.xtdb/tx-fns biff/tx-fns})

(defonce system (atom {}))

(defn start []
  (let [new-system (reduce (fn [system component]
                             (log/info "starting:" (str component))
                             (component system))
                           initial-system
                           components)]
    (reset! system new-system)
    (generate-assets! new-system)
    (log/info "Go to" (:biff/base-url new-system))))

(defn -main [& args]
  (start)
  (apply nrepl-cmd/-main args))

(defn refresh []
  (doseq [f (:biff/stop @system)]
    (log/info "stopping:" (str f))
    (f))
  (tn-repl/refresh :after `start))

(comment
 ;; Evaluate this if you make a change to initial-system, components, :tasks,
 ;; :queues, or config.edn. If you update secrets.env, you'll need to restart
 ;; the app.
 (refresh)

 ;; If that messes up your editor's REPL integration, you may need to use this
 ;; instead:
 (biff/fix-print (refresh))
 )
