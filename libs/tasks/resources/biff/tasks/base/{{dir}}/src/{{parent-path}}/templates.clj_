(ns {{parent-ns}}.templates
  (:require [rum.core :as rum]))

; Tip: for fancier emails, use Mailchimp's email editor, export to HTML, and
; then render it with Selmer.

(defn signin [{:keys [to url]}]
  {:to to
   :subject "Sign in to {{dir}}"
   :html (rum/render-static-markup
           [:div
            [:p "We received a request to sign in to {{dir}} using this email address."]
            [:p [:a {:href url :target "_blank"} "Click here to sign in."]]
            [:p "If you did not request this link, you can ignore this email."]])})
