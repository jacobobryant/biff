(ns demo
  (:require [com.biffweb.authenticate :as biff.auth]
            [clojure.string :as str]
            [dev.onionpancakes.chassis.core :as chassis]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.defaults :as defaults]
            [reitit.ring :as reitit-ring]
            [demo.store :as store]
            [nrepl.cmdline :as nrepl])
  (:gen-class))

;; Demo captcha: fails for fail@example.com, passes for everything else
(defn demo-captcha-verify [ctx]
  (let [email (some-> (get-in ctx [:params :email])
                      str/trim
                      str/lower-case)]
    {:success (not= email "fail@example.com")}))

(def demo-captcha-config
  {:biff.auth/captcha-verify      demo-captcha-verify
   :biff.auth/captcha-configured? (fn [_ctx] true)})

(defn send-email [_ctx
                  ;; also includes :to, :code, :html
                  {:keys [subject text to]}]
  (println)
  (println "---")
  (println "To:     " to)
  (println "Subject:" subject)
  (println)
  (println text)
  (println "---")
  (println)
  true)

(def auth-config
  (merge {:biff.auth/send-email send-email
          ;; where to redirect after a successful signin
          :biff.auth/app-path   "/app"
          ;; Set these keys to customize the signin page's appearance.
          :biff.auth/app-name   "Biff Auth Demo"
          ;; :biff.auth/logo-url      "https://example.com/logo.png"
          ;; :biff.auth/primary-color "blue"
          ;; :biff.auth/font-family   "green"
          }
         ;; For a real app, you can use one of the provider captcha
         ;; integrations:
         ;; - biff.auth/turnstile-config
         ;; - biff.auth/recaptcha-config
         ;; - biff.auth/hcaptcha-config
         demo-captcha-config))

;; Config known at compile time can be passed to biff.auth/routes (or
;; biff.auth/module) here; other config can be constructed at startup time and
;; merged into incoming Ring requests. See (store/atom-store).
(def auth-routes (biff.auth/routes auth-config))

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
         [:meta {:name    "viewport"
                 :content "width=device-width, initial-scale=1"}]
         [:title "Biff Auth Demo"]
         [:style (str "body { font-family: system-ui, sans-serif; "
                      "max-width: 600px; margin: 2rem auto; "
                      "padding: 0 1rem; }")]]
        [:body
         [:h1 "Welcome!"]
         [:p "You are signed in. User ID: " [:code (str uid)]]
         [:form {:method "post" :action biff.auth/signout-path}
          [:input {:type  "hidden"
                   :name  :__anti-forgery-token
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

(def handler
  (let [app-routes [["/" {:get home-page}]
                    ["/app" {:get app-page}]]
        all-routes (into auth-routes app-routes)]
    (reitit-ring/ring-handler
     (reitit-ring/router all-routes)
     not-found)))

(defn wrap-merge [handler m]
  (fn [request]
    (handler (merge m request))))

(defn -main [& _args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "8080"))]
    (jetty/run-jetty (-> #'handler
                         (defaults/wrap-defaults defaults/site-defaults)
                         (wrap-merge (store/atom-store)))
                     {:port port :join? false})
    (println "Started webserver on http://localhost:" port)
    (nrepl/-main "--port" "7888"
                 "--middleware" (pr-str '[cider.nrepl/cider-middleware]))
    @(promise)))
