(ns com.biffweb.authenticate.impl.frontend
  (:require [dev.onionpancakes.chassis.core :as chassis]))

(def send-code-path           "/_biff/auth/send-code")
(def send-link-path           "/_biff/auth/send-link")
(def verify-code-path         "/_biff/auth/verify-code")
(def verify-link-confirm-path "/_biff/auth/verify-link-confirm")
(def code-page-path           "/signin")
(def link-page-path           "/signup")
(def verify-link-page-path    "/signup/verify")

;;;; style helpers =============================================================

(defn- error-banner [error-code]
  (when error-code
    (let [send-failed (str "We couldn't send an email to that address. "
                           "Please try another.")
          errors      {"captcha" (str "Captcha verification failed. "
                                      "Please try again.")

                       "invalid-email"
                       "Invalid email address. Please try again."

                       "send-failed" send-failed

                       "invalid-link" (str "This link is invalid or expired. "
                                           "Please sign in again.")

                       "not-signed-in"
                       "You must be signed in to view that page."

                       "invalid-code"
                       "Invalid or expired code. Please try again."

                       "too-many-attempts" (str "Too many failed attempts. "
                                                "Please request a new code.")}]
      [:div {:style {:background    "#fef2f2"
                     :color         "#991b1b"
                     :border        "1px solid #fecaca"
                     :border-radius "0.375rem"
                     :padding       "0.75rem"
                     :margin-bottom "1rem"
                     :font-size     "0.875rem"}}
       (get errors error-code "An error occurred. Please try again.")])))

(defn- label [for-id text]
  [:label {:for   for-id
           :style {:display       "block"
                   :font-size     "0.875rem"
                   :font-weight   "500"
                   :margin-bottom "0.375rem"}}
   text])

(defn- input [attrs]
  [:input (merge {:style {:width         "100%"
                          :padding       "0.625rem 0.75rem"
                          :border        "1px solid #d1d5db"
                          :border-radius "0.375rem"
                          :font-size     "1rem"
                          :outline       "none"
                          :box-sizing    "border-box"}}
                 attrs)])

(defn- button [primary-color extra-attrs text]
  [:button (merge {:type  "submit"
                   :style {:display       "block"
                           :width         "100%"
                           :padding       "0.625rem 1rem"
                           :margin-top    "1rem"
                           :background    primary-color
                           :color         "white"
                           :border        "none"
                           :border-radius "0.375rem"
                           :font-size     "1rem"
                           :font-weight   "600"
                           :cursor        "pointer"
                           :text-align    "center"}}
                  extra-attrs)
   text])

(defn- footer [_primary-color & children]
  [:div {:style {:text-align "center"
                 :margin-top "1rem"
                 :font-size  "0.875rem"
                 :color      "#6b7280"}}
   children])

(defn- link [primary-color attrs text]
  [:a (merge {:style {:color           primary-color
                      :text-decoration "underline"}} attrs)
   text])

(defn- captcha-enabled?
  [{:biff.auth/keys [skip-captcha captcha-configured?] :as ctx}]
  (and (not skip-captcha)
       captcha-configured?
       (captcha-configured? ctx)))

(defn- captcha-area [{:biff.auth/keys [captcha-widget] :as ctx}]
  (when-let [widget (and (captcha-enabled? ctx)
                         (captcha-widget ctx))]
    [:div {:style {:margin-top "0.75rem"}}
     widget]))

(defn- hidden-field [name value]
  [:input {:type "hidden" :name name :value value}])

(defn- tab-link [primary-color {:keys [active? href label]}]
  [:a {:href  href
       :style {:padding         "0.5rem 1rem"
               :text-decoration "none"
               :font-weight     "600"
               :font-size       "0.95rem"
               :border-bottom   (str "2px solid "
                                     (if active? primary-color "transparent"))
               :color           (if active? primary-color "#6b7280")}}
   label])

(defn- tab-bar [primary-color tabs]
  [:div {:style {:display         "flex"
                 :justify-content "center"
                 :gap             "0.5rem"
                 :margin-bottom   "1.5rem"
                 :border-bottom   "1px solid #e5e7eb"}}
   (mapv #(tab-link primary-color %) tabs)])

;;;; Page rendering ============================================================

(defn- base-page
  [{:biff.auth/keys [app-name captcha-head] :as opts} & content]
  [:html {:lang "en"}
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:title (str "Sign in | " app-name)]
    [:style (str "*, *::before, *::after { box-sizing: border-box; } "
                 "body { margin: 0; padding: 0; }")]
    (captcha-head opts)]
   [:body {:style {:font-family     "'Inter', system-ui, sans-serif"
                   :background      "#f3f4f6"
                   :color           "#111827"
                   :min-height      "100vh"
                   :display         "flex"
                   :flex-direction  "column"
                   :align-items     "center"
                   :justify-content "center"
                   :padding         "1rem"
                   :margin          "0"}}
    content]])

(defn- card [{:biff.auth/keys [app-name logo-url]} & content]
  (into
   [:div {:style {:background    "white"
                  :border-radius "0.5rem"
                  :padding       "2rem"
                  :width         "100%"
                  :max-width     "24rem"
                  :box-shadow    "0 1px 3px rgba(0,0,0,0.1)"}}
    (if logo-url
      [:img {:src   logo-url
             :alt   (str app-name " logo")
             :style {:display   "block"
                     :max-width "180px"
                     :margin    "0 auto 1.5rem"}}]
      [:h1 {:style {:font-size   "1.25rem"
                    :font-weight "700"
                    :text-align  "center"
                    :margin      "0 0 1.5rem 0"
                    :color       "#111827"}}
       app-name])]
   content))

(defn- title [text]
  [:h2 {:style {:font-size   "1.5rem"
                :font-weight "700"
                :text-align  "center"
                :margin      "0 0 1.5rem 0"}}
   text])

(defn- render-signin-form
  [{:biff.auth/keys [primary-color
                     captcha-button-attrs]
    :keys           [anti-forgery-token]
    :as             ctx}]
  [:form {:method "post" :action send-code-path :id "signin-form"}
   (when anti-forgery-token
     (hidden-field "__anti-forgery-token" anti-forgery-token))
   (label "email" "Email address")
   (input {:type        "email"
           :name        "email"
           :id          "email"
           :placeholder "you@example.com"
           :required    true
           :autofocus   true})
   (captcha-area ctx)
   (button primary-color (captcha-button-attrs ctx)
           "Send sign-in code")])

(defn- render-signup-form
  [{:biff.auth/keys [primary-color
                     captcha-button-attrs]
    :keys           [anti-forgery-token]
    :as             ctx}]
  [:form {:method "post" :action send-link-path :id "signup-form"}
   (when anti-forgery-token
     (hidden-field "__anti-forgery-token" anti-forgery-token))
   (label "signup-email" "Email address")
   (input {:type        "email"
           :name        "email"
           :id          "signup-email"
           :placeholder "you@example.com"
           :required    true
           :autofocus   true})
   (captcha-area ctx)
   (button primary-color (captcha-button-attrs ctx)
           "Send sign-in link")])

(defn- render-verify-code
  [{:biff.auth/keys [primary-color code-page]
    :keys           [anti-forgery-token biff.stuff/params]}]
  (let [email (:email params)]
    [(title "Check your email")
     [:p {:style {:text-align "center"  :margin "0 0 1rem 0"
                  :font-size  "0.95rem"}}
      "We sent a 6-digit code to " [:strong email] "."]
     [:form {:method "post"
             :action verify-code-path
             :id     "verify-code-form"}
      (when anti-forgery-token
        (hidden-field "__anti-forgery-token" anti-forgery-token))
      (hidden-field "email" email)
      (label "code" "Verification code")
      (input {:type         "text"
              :name         "code"
              :id           "code"
              :required     true
              :autocomplete "one-time-code"
              :inputmode    "numeric"
              :maxlength    "6"
              :placeholder  "000000"
              :style        {:width          "100%"
                             :padding        "0.625rem 0.75rem"
                             :border         "1px solid #d1d5db"
                             :border-radius  "0.375rem"
                             :font-size      "1.25rem"
                             :outline        "none"
                             :box-sizing     "border-box"
                             :letter-spacing "0.2em"
                             :text-align     "center"}})
      (button primary-color {} "Verify")]
     (footer primary-color
             (link primary-color {:href code-page}
                   "← Use a different email"))]))

(defn- render-link-sent
  [{:biff.auth/keys [primary-color link-page]
    :keys           [biff.stuff/params]}]
  (let [email (:email params)]
    [(title "Check your email")
     [:p {:style {:text-align "center" :font-size "0.95rem" :margin "0"}}
      (if email
        ["We sent a sign-in link to " [:strong email] "."]
        "We sent you a sign-in link.")]
     (footer primary-color
             (link primary-color {:href link-page}
                   "← Back to sign in"))]))

(defn- render-link-confirm
  [{:biff.auth/keys [primary-color link-page]
    :keys           [anti-forgery-token biff.stuff/params]}]
  (let [token (:token params)]
    [(title "Confirm your email")
     [:p {:style {:text-align "center"
                  :margin     "0 0 1rem 0"
                  :font-size  "0.95rem"}}
      "Please enter your email address to complete sign-in."]
     [:form {:method "post"
             :action verify-link-confirm-path
             :id     "link-confirm-form"}
      (when anti-forgery-token
        (hidden-field "__anti-forgery-token" anti-forgery-token))
      (hidden-field "token" token)
      (label "confirm-email" "Email address")
      (input {:type        "email"
              :name        "email"
              :id          "confirm-email"
              :placeholder "you@example.com"
              :required    true
              :autofocus   true})
      (button primary-color {} "Confirm & sign in")]
     (footer primary-color
             (link primary-color {:href link-page}
                   "← Back to sign in"))]))

(defn- render-tabs
  [{:biff.auth/keys [primary-color code-page link-page] :as ctx} active-tab]
  [(tab-bar primary-color
            [{:active? (= active-tab :code)
              :href    code-page
              :label   "Sign In"}
             {:active? (= active-tab :link)
              :href    link-page
              :label   "Sign Up"}])
   (if (= active-tab :link)
     (render-signup-form ctx)
     (render-signin-form ctx))
   (footer primary-color
           (if (= active-tab :link)
             ["Already have an account? "
              (link primary-color {:href code-page} "Sign in")]
             ["Don't have an account? "
              (link primary-color {:href link-page} "Sign up")]))])

;;;; Main page handler =========================================================

(defn- page [{:keys [biff.stuff/params] :as ctx} content]
  {:status  200
   :headers {"content-type" "text/html"}
   :body    (chassis/html
             [chassis/doctype-html5
              (base-page
               ctx
               (card ctx
                     (error-banner (:error params))
                     content))])})

(defn code-page [{:keys [biff.stuff/params] :as ctx}]
  (page ctx (if (:email params)
              (render-verify-code ctx)
              (render-tabs ctx :code))))

(defn link-page [{:keys [biff.stuff/params] :as ctx}]
  (page ctx (if (:email params)
              (render-link-sent ctx)
              (render-tabs ctx :link))))

(defn verify-link-page [ctx]
  (page ctx (render-link-confirm ctx)))

(def routes
  [[code-page-path        {:get code-page}]
   [link-page-path        {:get link-page}]
   [verify-link-page-path {:get verify-link-page}]])
