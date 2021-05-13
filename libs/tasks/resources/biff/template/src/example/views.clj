(ns {{parent-ns}}.views
  (:require [biff.rum :as br]))

(def default-opts
  #:base{:title "Example app"
         :lang "en-US"
         :description "Here is my example app."})

(def head*
  [[:link {:rel "stylesheet" :href "/css/main.css"}]
   [:link {:rel "stylesheet" :href "/css/custom.css"}]])

(defn base [{:keys [base/head] :as opts} & contents]
  (br/base
    (merge
      default-opts
      opts
      {:base/head (concat head* head)})
    [:.p-3.mx-auto.max-w-prose.w-full contents]))

(def signin-form
  (list
    [:.text-lg "Email address:"]
    [:.h-3]
    [:form.mb-0 {:action "/api/send-token" :method "post"}
     [:.flex
      [:input.border.border-gray-500.rounded.p-2
       {:name "email" :type "email" :placeholder "Email"
        :value "abc@example.com"}]
      [:.w-3]
      [:button.btn {:type "submit"} "Sign in"]]]
    [:.h-1]
    [:.text-sm "Doesn't need to be a real address."]))

(def home
  (base
    {}
    signin-form
    [:script {:src "/js/ensure-signed-out.js"}]))

(def signin-sent
  (base
    {}
    [:p "Sign-in link sent, please check your inbox."]
    [:p.text-sm "(Just kidding: click on the sign-in link that was printed to the terminal.)"]))

(def signin-fail
  (base
    {}
    [:p "Invalid sign-in token."]
    signin-form))

(def app
  (base
    {}
    [:#app
     [:p "Loading..."]
     [:p "If you see this for more than a second, the ClojureScript build might not have finished. "
      "Go to " [:a.text-blue-500.hover:text-blue-800.hover:underline
                {:href "http://localhost:9630/build/app" :target "_blank"}
                "http://localhost:9630/build/app"]
      " and click on \"Watch\"."
      " After the build finishes, refresh this page."]
     [:p "If the build fails, you may have forgotten to run "
      [:code "./task init"] "."]
     [:script {:src "/js/ensure-signed-in.js"}]
     [:script {:src "/cljs/app/main.js"}]]))

(def not-found
  (base
    {}
    [:p "Not found."]))

(def static-pages
  {"/" home
   "/signin-sent/" signin-sent
   "/signin-fail/" signin-fail
   "/app/" app
   "/404.html" not-found})
