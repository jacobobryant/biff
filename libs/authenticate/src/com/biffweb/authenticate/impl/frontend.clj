(ns com.biffweb.authenticate.impl.frontend
  (:require [com.biffweb.authenticate.impl.backend :as backend]
            [dev.onionpancakes.chassis.core :as chassis]))

;; === Style helpers (inline styles instead of CSS classes) ===

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

(defn- auth-label [for-id text]
  [:label {:for   for-id
           :style {:display       "block"
                   :font-size     "0.875rem"
                   :font-weight   "500"
                   :margin-bottom "0.375rem"}}
   text])

(defn- auth-input [attrs]
  [:input (merge {:style {:width         "100%"
                          :padding       "0.625rem 0.75rem"
                          :border        "1px solid #d1d5db"
                          :border-radius "0.375rem"
                          :font-size     "1rem"
                          :outline       "none"
                          :box-sizing    "border-box"}}
                 attrs)])

(defn- auth-button [primary-color extra-attrs text]
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

(defn- auth-footer [_primary-color & children]
  (into [:div {:style {:text-align "center"
                       :margin-top "1rem"
                       :font-size  "0.875rem"
                       :color      "#6b7280"}}]
        children))

(defn- auth-link [primary-color attrs text]
  [:a (merge {:style {:color           primary-color
                      :text-decoration "underline"}} attrs)
   text])

(defn- captcha-enabled? [ctx]
  (boolean
   (when (not (:biff.auth/skip-captcha ctx))
     (when-let [configured? (:biff.auth/captcha-configured? ctx)]
       (configured? ctx)))))

(defn- captcha-area [ctx]
  (when (and (captcha-enabled? ctx)
             (:biff.auth/captcha-widget ctx))
    (let [widget-fn (:biff.auth/captcha-widget ctx)]
      [:div {:style {:margin-top "0.75rem"}}
       (widget-fn ctx)])))

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
  (into [:div {:style {:display         "flex"
                       :justify-content "center"
                       :gap             "0.5rem"
                       :margin-bottom   "1.5rem"
                       :border-bottom   "1px solid #e5e7eb"}}]
        (map #(tab-link primary-color %) tabs)))

;; === Page rendering ===

(defn- base-page [{:biff.auth/keys [app-name font-family] :as opts} & content]
  (let [captcha-head-fn (:biff.auth/captcha-head opts)]
    [:html {:lang "en"}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:title (str "Sign in | " app-name)]
      [:style (str "*, *::before, *::after { box-sizing: border-box; } "
                   "body { margin: 0; padding: 0; }")]
      (when (and (captcha-enabled? opts) captcha-head-fn)
        (captcha-head-fn opts))]
     (into
      [:body {:style {:font-family     font-family
                      :background      "#f3f4f6"
                      :color           "#111827"
                      :min-height      "100vh"
                      :display         "flex"
                      :flex-direction  "column"
                      :align-items     "center"
                      :justify-content "center"
                      :padding         "1rem"
                      :margin          "0"}}]
      content)]))

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
  [{:biff.auth/keys [primary-color] :keys [anti-forgery-token] :as ctx}]
  (let [captcha-btn-fn    (:biff.auth/captcha-button-attrs ctx)
        captcha-btn-attrs (when (and (captcha-enabled? ctx) captcha-btn-fn)
                            (captcha-btn-fn ctx))]
    [:form {:method "post" :action "/_biff/auth/send-code" :id "signin-form"}
     (when anti-forgery-token
       (hidden-field "__anti-forgery-token" anti-forgery-token))
     (auth-label "email" "Email address")
     (auth-input {:type        "email"           :name      "email" :id "email"
                  :placeholder "you@example.com"
                  :required    true              :autofocus true})
     (captcha-area ctx)
     (auth-button primary-color (or captcha-btn-attrs {})
                  "Send sign-in code")]))

(defn- render-signup-form
  [{:biff.auth/keys [primary-color] :keys [anti-forgery-token] :as ctx}]
  (let [captcha-btn-fn    (:biff.auth/captcha-button-attrs ctx)
        captcha-btn-attrs (when (and (captcha-enabled? ctx) captcha-btn-fn)
                            (captcha-btn-fn ctx))]
    [:form {:method "post" :action "/_biff/auth/send-link" :id "signup-form"}
     (when anti-forgery-token
       (hidden-field "__anti-forgery-token" anti-forgery-token))
     (auth-label "signup-email" "Email address")
     (auth-input {:type "email"

                  :name "email"

                  :id "signup-email"

                  :placeholder "you@example.com"

                  :required true

                  :autofocus true})
     (captcha-area ctx)
     (auth-button primary-color (or captcha-btn-attrs {})
                  "Send sign-in link")]))

(defn- render-verify-code
  [{:biff.auth/keys [primary-color code-signin-path]
    :keys           [anti-forgery-token params]}]
  (let [email (:email params)]
    (list
     (title "Check your email")
     [:p {:style {:text-align "center"  :margin "0 0 1rem 0"
                  :font-size  "0.95rem"}}
      "We sent a 6-digit code to " [:strong email] "."]
     [:form {:method "post"             :action "/_biff/auth/verify-code"
             :id     "verify-code-form"}
      (when anti-forgery-token
        (hidden-field "__anti-forgery-token" anti-forgery-token))
      (hidden-field "email" email)
      (auth-label "code" "Verification code")
      (auth-input {:type "text"

                   :name "code"

                   :id "code"

                   :required true

                   :autocomplete "one-time-code"

                   :pattern "[0-9]{6}"

                   :maxlength "6"

                   :placeholder "000000"

                   :style {:width          "100%"
                           :padding        "0.625rem 0.75rem"
                           :border         "1px solid #d1d5db"
                           :border-radius  "0.375rem"
                           :font-size      "1.25rem"
                           :outline        "none"
                           :box-sizing     "border-box"
                           :letter-spacing "0.2em"
                           :text-align     "center"}})
      (auth-button primary-color {} "Verify")]
     (auth-footer primary-color
                  (auth-link primary-color {:href code-signin-path}
                             "← Use a different email")))))

(defn- render-link-sent
  [{:biff.auth/keys [primary-color link-signin-path] :keys [params] :as _ctx}]
  (let [email (:email params)]
    (list
     (title "Check your email")
     [:p {:style {:text-align "center" :font-size "0.95rem" :margin "0"}}
      (if email
        (list "We sent a sign-in link to " [:strong email] ".")
        "We sent you a sign-in link.")]
     (auth-footer primary-color
                  (auth-link primary-color {:href link-signin-path}
                             "← Back to sign in")))))

(defn- render-link-confirm
  "Renders a form asking the user to enter their email for session fixation
   protection."
  [{:biff.auth/keys [primary-color link-signin-path]
    :keys           [anti-forgery-token params]      :as _ctx}]
  (let [token (:token params)]
    (list
     (title "Confirm your email")
     [:p {:style {:text-align "center"  :margin "0 0 1rem 0"
                  :font-size  "0.95rem"}}
      "Please enter your email address to complete sign-in."]
     [:form {:method "post"
             :action "/_biff/auth/verify-link-confirm"
             :id     "link-confirm-form"}
      (when anti-forgery-token
        (hidden-field "__anti-forgery-token" anti-forgery-token))
      (hidden-field "token" token)
      (auth-label "confirm-email" "Email address")
      (auth-input {:type "email"

                   :name "email"

                   :id "confirm-email"

                   :placeholder "you@example.com"

                   :required true

                   :autofocus true})
      (auth-button primary-color {} "Confirm & sign in")]
     (auth-footer primary-color
                  (auth-link primary-color {:href link-signin-path}
                             "← Back to sign in")))))

(defn- render-tabs
  [{:biff.auth/keys [primary-color code-signin-path link-signin-path]
    :keys           [params]                                          :as ctx}]
  (let [active-tab (or (:tab params) "signin")]
    (list
     (tab-bar primary-color
              [{:active? (= active-tab "signin")
                :href    (backend/append-query-params
                          code-signin-path "tab=signin")
                :label   "Sign In"}
               {:active? (= active-tab "signup")
                :href    (backend/append-query-params
                          link-signin-path "tab=signup")
                :label   "Sign Up"}])
     (if (= active-tab "signup")
       (render-signup-form ctx)
       (render-signin-form ctx))
     (auth-footer primary-color
                  (if (= active-tab "signup")
                    (list "Already have an account? "
                          (auth-link primary-color
                                     {:href (backend/append-query-params
                                             code-signin-path "tab=signin")}
                                     "Sign in"))
                    (list "Don't have an account? "
                          (auth-link primary-color
                                     {:href (backend/append-query-params
                                             link-signin-path "tab=signup")}
                                     "Sign up")))))))

;; === Main page handler ===

(defn signin-page
  "Renders the signin page. Looks at query params to decide the view:
   - ?verify=code&email=... → verify code form
   - ?verify=link&email=... → link sent message
   - ?verify=link-confirm&token=... → email confirmation form
   - ?tab=signup → sign up form (send link)
   - default → sign in form (send code)
  Returns a Ring response map with rendered HTML."
  [{:keys [params] :as ctx}]
  (let [verify (:verify params)
        error  (:error params)
        html   (base-page ctx
                          (card ctx
                                (error-banner error)
                                (cond
                                  (= verify "code")
                                  (render-verify-code ctx)

                                  (= verify "link")
                                  (render-link-sent ctx)

                                  (= verify "link-confirm")
                                  (render-link-confirm ctx)

                                  :else
                                  (render-tabs ctx))))]
    {:status  200
     :headers {"content-type" "text/html"}
     :body    (chassis/html [chassis/doctype-html5 html])}))
