(ns {{parent-ns}}.routes
  (:require
    [biff.http :as bhttp]
    [biff.util :as bu]
    [crux.api :as crux]
    [ring.middleware.anti-forgery :as anti-forgery]))

; See https://findka.com/biff/#http-routes

; Test it out:
; curl http://localhost:8080/echo?foo=bar
; curl -XPOST http://localhost:8080/echo -F foo=bar
; curl -XPOST http://localhost:8080/echo -H 'Content-Type: application/edn' -d '{:foo "bar"}'
; curl -XPOST http://localhost:8080/echo -H 'Content-Type: application/json' -d '{"foo":"bar"}'
(defn echo [req]
  ; Default :status is 200. Default :body is "". :headers/* and
  ; :cookies/* are converted to `:headers {...}` and `:cookies {...}`.
  {:headers/Content-Type "application/edn"
   :body (prn-str (merge
                    (select-keys req [:params :body-params])
                    (bu/select-ns req 'params)))})

; These require authentication, so you'll have to test them from the browser.
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

; See https://cljdoc.org/d/metosin/reitit/0.5.10/doc/introduction#ring-router
(def routes
  [["/echo" {:get echo
             :post echo
             :name ::echo}]
   ["/whoami" {:post whoami
               :middleware [anti-forgery/wrap-anti-forgery]
               :name ::whoami}]
   ; Same as whoami
   ["/whoami2" {:post whoami2
                :middleware [bhttp/wrap-authorize]
                :name ::whoami2}]])
