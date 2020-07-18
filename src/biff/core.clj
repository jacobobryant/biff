(ns ^:biff biff.core
  (:require
    [clojure.edn :as edn]
    [clojure.java.classpath :as cp]
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.tools.namespace.find :as tn-find]
    [clojure.tools.namespace.repl :as tn-repl]
    [orchestra.spec.test :as st]
    [nrepl.server :as nrepl]
    [biff.system :refer [start-biff]]
    [trident.util :as u]
    [taoensso.timbre :as timbre :refer [log spy]]
    [immutant.web :as imm]))

(defonce system (atom nil))

(defn try-requiring-resolve [sym]
  (try
    (requiring-resolve sym)
    (catch Exception e
      (println (str "Couldn't require " (namespace sym) ":"))
      (.printStackTrace e)
      nil)))

(defn get-components []
  (->> (cp/classpath)
    tn-find/find-ns-decls
    (map second)
    (filter (comp :biff meta))
    (mapcat #(some-> %
               name
               (symbol "components")
               try-requiring-resolve
               deref))))

(defn start
  ([]
   (start (get-components)))
  ([components]
   (apply println "Starting" (map :name components))
   (reset! system (u/start-system components))
   (println "System started.")
   nil))

(defn stop []
  (u/stop-system @system))

(defn refresh []
  (stop)
  (tn-repl/refresh :after `start))

(def set-first-start
  {:name ::set-first-start
   :required-by [:biff/init]
   :start #(merge {:biff/first-start true} %)})

(defn -main []
  (start (conj (get-components) set-first-start)))

(defn get-config [env]
  (some-> "config.edn"
    u/maybe-slurp
    edn/read-string
    (u/merge-config env)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def init
  {:name :biff/init
   :start (fn [sys]
            (let [env (keyword (or (System/getenv "BIFF_ENV") :prod))
                  {:keys [first-start]
                   :biff.init/keys [start-nrepl nrepl-port instrument timbre]
                   :or {start-nrepl true nrepl-port 7888 timbre true}
                   :as sys} (merge sys (get-config env))]
              (when timbre
                (timbre/merge-config! (u/select-ns-as sys 'timbre nil)))
              (when instrument
                (s/check-asserts true)
                (st/instrument))
              (when (and first-start start-nrepl nrepl-port)
                (nrepl/start-server :port nrepl-port))
              sys))})

(def console
  {:name :biff/console
   :requires [:biff/init]
   :required-by [:biff/web-server]
   :start (fn [{:keys [biff.console/enabled] :as sys}]
            ; I'll enable this by default after we actually have a web console app.
            (if enabled
              (-> sys
                (merge #:console.biff.auth{:on-signin "/"
                                           :on-signin-request "/biff/signin-request"
                                           :on-signin-fail "/biff/signin-fail"
                                           :on-signout "/biff/signin"})
                (start-biff 'console))
              sys))})

(def web-server
  {:name :biff/web-server
   :requires [:biff/init]
   :start (fn [{:biff.web/keys [host->handler port]
                :or {port 8080} :as sys}]
            (let [server (imm/run
                           #(if-some [handler (get host->handler (:server-name %))]
                              (handler %)
                              {:status 404
                               :body "Not found."
                               :headers {"Content-Type" "text/plain"}})
                           {:port port})]
              (update sys :sys/stop conj #(imm/stop server))))})

(def components [init console web-server])

(comment
  (u/pprint (deref system))
  (refresh)
  (u/stop-system @system))
