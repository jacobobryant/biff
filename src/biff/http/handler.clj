(ns biff.http.handler
  (:require
    [biff.util :as bu]
    [biff.core :as core]
    [mount.core :refer [defstate]]
    [ring.middleware.defaults :as rd]
    [ring.middleware.session.cookie :as cookie]
    [byte-transforms :as bt]
    [byte-streams :as bs]
    [crypto.random :as random]
    [clojure.java.io :as io]
    [reitit.ring :as reitit]
    [trident.util :as u]
    ))

(def secret-key-path "data/biff.http/secret-key")

(defn secret-key []
  (or
    (u/catchall (bt/decode (slurp secret-key-path) :base64))
    (let [k (random/bytes 16)
          k-str (bs/to-string (bt/encode k :base64))]
      (io/make-parents secret-key-path)
      (spit secret-key-path k-str)
      k)))

(defn redirect-home [home _]
  {:status 302
   :headers {"Location" home}
   :body ""})

(defn make-default-handler [{:keys [main plugins]}]
  (let [routes (->> plugins
                 vals
                 (map :biff.http/route)
                 (filterv some?))
        home (:biff.http/home main (some :biff.http/home (vals plugins)))]
    (reitit/ring-handler
      (reitit/router
        (into [["/" {:get (partial redirect-home home)
                     :name ::home}]]
          routes)
        {:data {:middleware [[rd/wrap-defaults (bu/ring-settings core/debug (secret-key))]]}})
      (reitit/routes
        (reitit/create-resource-handler {:path "/"})
        (reitit/create-default-handler)))))

(defstate default-handler
  :start (make-default-handler core/config))
