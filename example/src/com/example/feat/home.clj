(ns com.example.feat.home
  (:require [com.biffweb :as biff]
            [com.example.views :as v]))

(defn signin-form []
  (biff/form
    {:action "/auth/send/"}
    [:div [:label {:for "email"} "Email address:"]]
    [:.h-1]
    [:.flex
     [:input#email
      {:name "email"
       :type "email"
       :placeholder "abc@example.com"}]
     ;; Even though we're using email links for authentication, include this so
     ;; password managers will hopefully save the email address.
     ;; TODO see if this actually works, or if there's some way to make it work.
     [:input.hidden {:type "password"
                     :name "password"}]
     [:.w-3]
     [:button.btn {:type "submit"} "Sign in"]]
    [:.h-1]
    [:.text-sm
     "Doesn't need to be a real address. "
     "Until you add an API key for MailerSend, we'll just print your sign-in "
     "link to the console."]))

(defn home [_]
  (biff/render
    (v/page
      {}
      nil
      (signin-form))))

(def features
  {:routes [["/" {:get home}]]})
