(ns {{parent-ns}}.routes
  (:require [biff.crux :as bcrux]
            [biff.misc :as misc]
            [biff.rum :as br]
            [biff.util :as bu]
            [clojure.edn :as edn]
            [clojure.stacktrace :as st]
            [clojure.walk :refer [postwalk]]
            [crux.api :as crux]
            [{{parent-ns}}.routes.auth :as auth]
            [{{parent-ns}}.views :as v]))

; See https://biff.findka.com/codox/biff.middleware.html#var-wrap-flat-keys

; Test it out:
; curl http://localhost:8080/echo?foo=bar
; curl -XPOST http://localhost:8080/echo -F foo=bar
; curl -XPOST http://localhost:8080/echo -H 'Content-Type: application/edn' -d '{:foo "bar"}'
; curl -XPOST http://localhost:8080/echo -H 'Content-Type: application/json' -d '{"foo":"bar"}'
(defn echo [req]
  ; Default :status is 200. Default :body is "". :headers/* and
  ; :cookies/* are converted to `:headers {...}` and `:cookies {...}`.
  {:status 200
   :body (merge
           (select-keys req [:params :body-params])
           (bu/select-ns req 'params))})

; Go to http://localhost:8080/api/whoami after signing in.
(defn whoami [{:keys [biff/uid biff.crux/db]}]
  {:status 200
   :body (:user/email (crux/entity @db uid))
   :headers/Content-Type "text/plain"})

(defn on-error [{:keys [status uid]}]
  (if (or (= 401 status)
          (and (= 403 status) (not uid)))
    {:status 302
     :headers/Location "/"}
    {:status status
     :headers/Content-Type "text/html"
     :body (str "<h1>" (get bu/http-status->msg status "There was an error.") "</h1>")}))

(defn wrap-signed-in [handler]
  (fn [{:keys [biff/uid] :as req}]
    (if (some? uid)
      (handler req)
      (on-error (assoc req :status 401)))))

; See https://biff.findka.com/#receiving-transactions
(defn form-tx [req]
  (let [[biff-tx path] (misc/parse-form-tx
                         req
                         {:coercions {:text identity}})]
    (bcrux/submit-tx (assoc req :biff.crux/authorize true) biff-tx)
    {:status 302
     :headers/location path}))

; See https://cljdoc.org/d/metosin/reitit/0.5.10/doc/introduction#ring-router
(defn routes []
  [["/api"
    ["/echo" {:get echo
              :post echo}]
    ["/whoami" {:get whoami
                :middleware [wrap-signed-in]}]
    ["/form-tx" {:post form-tx}]]
   ["/app/ssr" {:get #(br/render v/ssr %)
                :middleware [wrap-signed-in]
                :name :ssr
                :biff/redirect true}]
   auth/routes])
