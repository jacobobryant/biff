(ns demo
  (:require [com.biffweb.authenticate :as auth]
            [clojure.string :as str]
            [dev.onionpancakes.chassis.core :as chassis]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.defaults :as defaults]
            [reitit.ring :as reitit-ring]
            [demo.store :as store])
  (:gen-class))

;; Demo captcha: fails for fail@example.com, passes for everything else
(defn demo-captcha-verify [ctx]
  (let [email (some-> (get-in ctx [:params :email])
                      str/trim
                      str/lower-case)]
    {:success (not= email "fail@example.com")}))

;; Set up auth module
(def auth-config
  (merge (store/atom-store)
         {:biff.auth/verify-captcha demo-captcha-verify
          :biff.auth/app-path       "/app"
          :biff.auth/app-name       "Biff Auth Demo"}))

(def auth-module (auth/module auth-config))

(defn render [hiccup-form]
  {:status  200
   :headers {"content-type" "text/html"}
   :body    (chassis/html [chassis/doctype-html5 hiccup-form])})

(defn app-page [req]
  (let [uid (get-in req [:session :uid])]
    (if uid
      (render
       [:html {:lang "en"}
        [:head
         [:meta {:charset "utf-8"}]
         [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
         [:title "Biff Auth Demo"]
         [:style "body { font-family: system-ui, sans-serif; max-width: 600px; margin: 2rem auto; padding: 0 1rem; }"]]
        [:body
         [:h1 "Welcome!"]
         [:p "You are signed in. User ID: " [:code (str uid)]]
         [:form {:method "post" :action "/_biff/auth/signout"}
          [:input {:type  "hidden"                  :name "__anti-forgery-token"
                   :value (:anti-forgery-token req)}]
          [:button {:type  "submit"
                    :style {:padding       "0.5rem 1rem"
                            :background    "#4F46E5"
                            :color         "white"
                            :border        "none"
                            :border-radius "0.375rem"
                            :cursor        "pointer"}}
           "Sign out"]]]])
      {:status  303
       :headers {"location" "/signin?error=not-signed-in"}})))

(defn home-page [_req]
  {:status  303
   :headers {"location" "/signin"}})

(defn not-found [_req]
  {:status 404 :body "Not found"})

;; Build reitit router from auth module routes + app routes
(defn make-handler []
  (let [auth-routes (:biff.ring/routes auth-module)
        app-routes  [["/" {:get home-page}]
                     ["/app" {:get app-page}]]
        all-routes  (into auth-routes app-routes)]
    (reitit-ring/ring-handler
     (reitit-ring/router all-routes)
     not-found)))

(defn -main [& _args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "8080"))]
    (println (str "Starting demo server on port " port "..."))
    (jetty/run-jetty (defaults/wrap-defaults (make-handler)
                                             (-> defaults/site-defaults
                                                 (assoc-in [:security :anti-forgery] true)
                                                 (assoc-in [:session :cookie-attrs :same-site] :lax)))
                     {:port port :join? true})))
