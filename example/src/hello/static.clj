(ns hello.static
  (:require
    [rum.core :as rum]))

(def signin-form
  (rum/fragment
    [:p "Email address:"]
    [:form {:action "/api/signin-request" :method "post"}
     [:input {:name "email" :type "email" :placeholder "Email"}]
     [:button {:type "submit"} "Sign up/Sign in"]]))

(def home
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:script {:src "/js/ensure-signed-out.js"}]]
   [:body
    signin-form]])

(def signin-sent
  [:html [:head [:meta {:charset "utf-8"}]]
   [:body
    [:p "Sign-in link sent, please check your inbox."]
    [:p "(Just kidding: click on the sign-in link that was printed to the console.)"]]])

(def signin-fail
  [:html [:head [:meta {:charset "utf-8"}]]
   [:body
    [:p "Invalid sign-in token."]
    signin-form]])

(def app
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:script {:src "/js/ensure-signed-in.js"}]]
   [:body
    [:#app "Loading..."]
    [:script {:src "/cljs/app/main.js"}]]])

(def not-found
  [:html [:head [:meta {:charset "utf-8"}]]
   [:body
    [:p "Not found."]]])

(def pages
  {"/" home
   "/signin/sent/" signin-sent
   "/signin/fail/" signin-fail
   "/app/" app
   "/404.html" not-found})
