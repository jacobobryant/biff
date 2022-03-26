(ns com.example.feat.home
  (:require [com.biffweb :as biff]
            [com.example.ui :as ui]))

(defn signin-form []
  (biff/form
    {:action "/auth/send"}
    [:div [:label {:for "email"} "Email address:"]]
    [:.h-1]
    [:.flex
     [:input#email
      {:name "email"
       :type "email"
       :autocomplete "email"
       :placeholder "abc@example.com"}]
     [:.w-3]
     [:button.btn {:type "submit"} "Sign in"]]
    [:.h-1]
    [:.text-sm
     "Doesn't need to be a real address. "
     "Until you add an API key for MailerSend, we'll just print your sign-in "
     "link to the console."]))

(defn home [_]
  (ui/page
    {}
    (signin-form)))

(def features
  {:routes [["/" {:get home}]]})
