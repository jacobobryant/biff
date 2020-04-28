(ns ^:biff biff.core
  (:require
    [clojure.java.classpath :as cp]
    [clojure.string :as str]
    [clojure.tools.namespace.find :as tn-find]
    [clojure.tools.namespace.repl :as tn-repl]
    [orchestra.spec.test :as st]
    [nrepl.server :as nrepl]
    [biff.util :as bu]
    [trident.util :as u]
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
   (reset! system (bu/start-system components))))

(defn refresh []
  (bu/stop-system @system)
  (start)
  #_(tn-repl/refresh :after 'biff.core/start))

(def toggle-nrepl
  {:name ::toggle-nrepl
   :required-by [:biff/core]
   :start #(assoc % :biff.core/start-nrepl true)})

(defn -main []
  (start (conj (get-components) toggle-nrepl)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def core
  {:name :biff/core
   :start (fn [sys]
            (let [env (keyword (or (System/getenv "BIFF_ENV") :prod))
                  {:biff.core/keys [start-nrepl nrepl-port instrument]
                   :or {nrepl-port 7888} :as sys} (merge sys (bu/get-config env))]
              (when instrument
                (st/instrument))
              (when (and start-nrepl nrepl-port)
                (nrepl/start-server :port nrepl-port))
              sys))})

(def web-server
  {:name :biff/web-server
   :requires [:biff/core]
   :start (fn [{:biff.web/keys [host->handler port] :as sys}]
            (let [server (imm/run
                           #(if-some [handler (get host->handler (:server-name %))]
                              (handler %)
                              {:status 404
                               :body "Not found."
                               :headers {"Content-Type" "text/plain"}})
                           {:port port})]
              (update sys :trident.system/stop conj #(imm/stop server))))})


(def components [core web-server])

(comment
  (u/pprint (deref system))
  (refresh)
  (bu/stop-system @system))
