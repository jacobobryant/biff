(ns example.routes
  (:require
    [biff.util :as bu]
    [crux.api :as crux]
    [ring.middleware.anti-forgery :as anti-forgery]))

(defn echo [req]
  {:headers/Content-Type "application/edn"
   :body (prn (merge
                (select-keys req [:params :body-params])
                (bu/select-ns req 'params)))})

(defn whoami [{:keys [session/uid biff/db]}]
  (if (some? uid)
    {:body (:user/email (crux/entity db {:user/id uid}))
     :headers/Content-Type "text/plain"}
    {:status 401
     :headers/Content-Type "text/plain"
     :body "Not authorized."}))

(defn whoami2 [{:keys [session/uid biff/db]}]
  {:body (:user/email (crux/entity db {:user/id uid}))
   :headers/Content-Type "text/plain"})

(def routes
  [["/echo" {:get echo
             :post echo
             :name ::echo}]
   ["/whoami" {:post whoami
               :middleware [anti-forgery/wrap-anti-forgery]
               :name ::whoami}]
   ; Same as whoami
   ["/whoami2" {:post whoami2
                :middleware [bu/wrap-authorize]
                :name ::whoami2}]])
