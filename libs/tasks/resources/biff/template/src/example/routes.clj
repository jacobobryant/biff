(ns {{parent-ns}}.routes
  (:require [biff.crux :as bcrux]
            [biff.glue :as glue]
            [biff.rum :as br]
            [biff.util :as bu]
            [clojure.edn :as edn]
            [clojure.stacktrace :as st]
            [clojure.walk :refer [postwalk]]
            [crux.api :as crux]
            [ring.middleware.anti-forgery :as anti-forgery]
            [{{parent-ns}}.routes.auth :as auth]
            [{{parent-ns}}.views :as v]
            [{{parent-ns}}.views.shared :as shared]))

; See https://biff.findka.com/#http-routes

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

; This requires authentication, so you'll have to test it from the browser.
(defn whoami [{:keys [biff/uid biff.crux/db] :as sys}]
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

(defn ssr [{:keys [biff/uid biff.crux/db params/submitted]}]
  (let [{:user/keys [email foo]} (crux/pull @db [:user/email :user/foo] uid)]
    (v/base
      {}
      [:div
       (shared/header {:email (delay email)})
       [:.h-6]
       (shared/tabs {:active-id (delay :ssr)
                     :tab-data [{:id :crud
                                 :href "/app"
                                 :label "CRUD"}
                                {:id :db
                                 :href "/app/db"
                                 :label "DB Contents"}
                                {:id :ssr
                                 :href "/app/ssr"
                                 :label "SSR"}]})
       [:.h-3]
       [:div "This tab uses server-side rendering instead of React."]
       [:.h-6]
       (br/form
         {:action "/api/form-tx"
          :hidden {"__anti-forgery-token" anti-forgery/*anti-forgery-token*
                   "tx-info"
                   (pr-str
                     {:tx {[:user uid] {:db/update true
                                        :user/foo 'foo}}
                      :fields {'foo :text}
                      :redirect ::ssr
                      :query-params {:submitted true}})}}
         [:.text-lg "Foo: " [:span.font-mono (pr-str foo)]]
         [:.text-sm.text-gray-600
          "This demonstrates submitting a Biff transaction via an HTML form."]
         [:.h-1]
         [:.flex
          [:input.input-text.w-full {:name "foo"
                                     :value foo}]
          [:.w-3]
          [:button.btn {:type "submit"} "Update"]]
         (when submitted
           [:.font-bold.my-3 "Transaction submitted successfully."]))])))

(defn form-tx [req]
  (glue/handle-form-tx req {:coercions {:text identity}}))

; See https://cljdoc.org/d/metosin/reitit/0.5.10/doc/introduction#ring-router
(defn routes []
  [["/api/echo" {:get echo
                 :post echo}]
   ["/api/whoami" {:get whoami
                   :middleware [wrap-signed-in]}]
   ["/app/ssr" {:get #(br/render ssr %)
                :middleware [wrap-signed-in]
                :name ::ssr
                :biff/redirect true}]
   ["/api/form-tx" {:post form-tx}]
   (auth/routes)])
