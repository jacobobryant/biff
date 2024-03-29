---
title: Make a landing page
---

[View the code for this section](https://github.com/jacobobryant/eelchat/commit/56f951a092fcac57c920123ce4918e94c5907978).

Now that we've stripped our app down to the essentials, let's start building it
back up. For now we'll just do a little branding. The app won't do anything
yet, but at least you'll have a landing page. You can start collecting emails
for a waitlist!

Let's go to [localhost:8080](http://localhost:8080) once more. If you're signed
in, hit the `Sign out` button. You should be redirected to the landing page:

![A screenshot of the landing page before any changes are made](/img/tutorial/landing-page-1.png)

We'll update the site metadata first. Go to `com.eelchat.settings` and change the
application name from `My Application` to `eelchat`.


```diff
;; src/com/eelchat/settings.clj
;; ...
-(def app-name "My Application")
+(def app-name "eelchat")
```

Then edit `com.eelchat.ui` and update the description metadata:

```diff
;; src/com/eelchat/ui.clj
;; ...
        (merge #:base{:title settings/app-name
                      :lang "en-US"
                      :icon "/img/glider.png"
-                     :description (str settings/app-name " Description")
+                     :description "The world's finest discussion platform."
                      :image "https://clojure.org/images/clojure-logo-120b.png"})
        (update :base/head (fn [head]
```

I've taken the liberty of designing a simple logo for eelchat. Add the files to your project:

```plaintext
$ %%cd resources/public/%%

$ %%wget https://biffweb.com/eelchat-assets.zip%%
--2022-11-04 14:16:12--  https://biffweb.com/eelchat-assets.zip
[...]

$ %%unzip eelchat-assets.zip%%
Archive:  eelchat-assets.zip
  inflating: android-chrome-192x192.png
  inflating: android-chrome-512x512.png
  inflating: apple-touch-icon.png
  inflating: browserconfig.xml
  inflating: favicon-16x16.png
  inflating: favicon-32x32.png
  inflating: favicon.ico
  inflating: img/eel.svg
  inflating: img/logo.png
  inflating: mstile-144x144.png
  inflating: mstile-150x150.png
  inflating: mstile-310x150.png
  inflating: mstile-310x310.png
  inflating: mstile-70x70.png
  inflating: safari-pinned-tab.svg
  inflating: site.webmanifest

$ %%rm eelchat-assets.zip%%
```

Then add the appropriate metadata to `ui.clj`:

```diff
;; src/com/eelchat/ui.clj
;; ...
    (-> ctx
        (merge #:base{:title settings/app-name
                      :lang "en-US"
-                     :icon "/img/glider.png"
                      :description "The world's finest discussion platform."
-                     :image "https://clojure.org/images/clojure-logo-120b.png"})
+                     :image "/img/logo.png"})
        (update :base/head (fn [head]
                             (concat [[:link {:rel "stylesheet" :href (css-path)}]
                                      [:script {:src (js-path)}]
                                      [:script {:src "https://unpkg.com/htmx.org@1.9.0"}]
                                      [:script {:src "https://unpkg.com/htmx.org/dist/ext/ws.js"}]
                                      [:script {:src "https://unpkg.com/hyperscript.org@0.9.8"}]
+                                     [:link {:href "/apple-touch-icon.png", :sizes "180x180", :rel "apple-touch-icon"}]
+                                     [:link {:href "/favicon-32x32.png", :sizes "32x32", :type "image/png", :rel "icon"}]
+                                     [:link {:href "/favicon-16x16.png", :sizes "16x16", :type "image/png", :rel "icon"}]
+                                     [:link {:href "/site.webmanifest", :rel "manifest"}]
+                                     [:link {:color "#5bbad5", :href "/safari-pinned-tab.svg", :rel "mask-icon"}]
+                                     [:meta {:content "#da532c", :name "msapplication-TileColor"}]
+                                     [:meta {:content "#0d9488", :name "theme-color"}]
                                      (when recaptcha
                                        [:script {:src "https://www.google.com/recaptcha/api.js"
                                                  :async "async" :defer "defer"}])]
                                     head))))
```

Go to [localhost:8080/img/logo.png](http://localhost:8080/img/logo.png) if
you'd like to see the logo. It'll be displayed in social media embeds if you
post a link to your site.

Some tips for any fellow non-graphic-designers: I made the logo with
[Canva](https://www.canva.com/), [Noun Project](https://thenounproject.com/),
[MyFonts](https://www.myfonts.com/), and
[Tailwind's color pallete](https://tailwindcss.com/docs/customizing-colors). Then I generated some icons
with [RealFaviconGenerator](https://realfavicongenerator.net), using the
indispensable [HTML To Hiccup](https://htmltohiccup.herokuapp.com/)
(RIP—use [html2hiccup](http://html2hiccup.buttercloud.com/) instead)
for conversion—the resulting code is shown above in the `:base/head` section.

Now go to `com.eelchat.home`. Remove the `home-page` function:

```diff
;; src/com/eelchat/home.clj
;; ...
-(defn home-page [{:keys [recaptcha/site-key params] :as ctx}]
-  (ui/page
-   (assoc ctx ::ui/recaptcha true)
-   (biff/form
-    {:action "/auth/send-link"
-     :id "signup"
-     :hidden {:on-error "/"}}
-    (biff/recaptcha-callback "submitSignup" "signup")
-    [:h2.text-2xl.font-bold (str "Sign up for " settings/app-name)]
-    [:.h-3]
-    [:.flex
-     [:input#email {:name "email"
-                    :type "email"
-                    :autocomplete "email"
-                    :placeholder "Enter your email address"}]
-     [:.w-3]
-     [:button.btn.g-recaptcha
-      (merge (when site-key
-               {:data-sitekey site-key
-                :data-callback "submitSignup"})
-             {:type "submit"})
-      "Sign up"]]
-    (when-some [error (:error params)]
-      [:<>
-       [:.h-1]
-       [:.text-sm.text-red-600
-        (case error
-          "recaptcha" (str "You failed the recaptcha test. Try again, "
-                           "and make sure you aren't blocking scripts from Google.")
-          "invalid-email" "Invalid email. Try again with a different address."
-          "send-failed" (str "We weren't able to send an email to that address. "
-                             "If the problem persists, try another address.")
-          "There was an error.")]])
-    [:.h-1]
-    [:.text-sm "Already have an account? " [:a.link {:href "/signin"} "Sign in"] "."]
-    [:.h-3]
-    biff/recaptcha-disclosure
-    email-disabled-notice)))
```

And replace it with the following two functions:

```clojure
;; src/com/eelchat/home.clj
;; ...
(defn signup-form [{:keys [recaptcha/site-key params]}]
  (biff/form
   {:id "signup"
    :action "/auth/send-link"
    :hidden {:on-error "/"}
    :class "sm:max-w-xs w-full"}
   (biff/recaptcha-callback "submitSignup" "signup")
   [:input#email
    {:name "email"
     :type "email"
     :autocomplete "email"
     :placeholder "Enter your email address"
     :class '[border
              border-gray-300
              rounded
              w-full
              focus:border-teal-600
              focus:ring-teal-600]}]
   [:.h-3]
   [:button
    (merge (when site-key
             {:data-sitekey site-key
              :data-callback "submitSignup"})
           {:type "submit"
            :class '[bg-teal-600
                     hover:bg-teal-800
                     text-white
                     py-2
                     px-4
                     rounded
                     w-full
                     g-recaptcha]})
    "Join the waitlist"]
   (when-some [error (:error params)]
     [:<>
      [:.h-1]
      [:.text-sm.text-red-600
       (case error
         "recaptcha" (str "You failed the recaptcha test. Try again, "
                          "and make sure you aren't blocking scripts from Google.")
         "invalid-email" "Invalid email. Try again with a different address."
         "send-failed" (str "We weren't able to send an email to that address. "
                            "If the problem persists, try another address.")
         "There was an error.")]])))

(defn home-page [ctx]
  (ui/base
   (assoc ctx ::ui/recaptcha true)
   [:.bg-orange-50.flex.flex-col.flex-grow.items-center.p-3
    [:.h-12.grow]
    [:img.w-40 {:src "/img/eel.svg"}]
    [:.h-6]
    [:.text-2xl.sm:text-3xl.font-semibold.sm:text-center.w-full
     "The world's finest discussion platform"]
    [:.h-2]
    [:.sm:text-lg.sm:text-center.w-full
     "Communities, channels, messages, even RSS—eelchat has it all. Coming soon."]
    [:.h-6]
    (signup-form ctx)
    [:.h-12 {:class "grow-[2]"}]
    [:.text-sm biff/recaptcha-disclosure]
    [:.h-6]]))
```

You can use [the Tailwind website](https://tailwindcss.com/) to look up
the utility classes above, such as `h-12` etc.

The landing page will look like this:

![Screenshot of the finished landing page](/img/tutorial/landing-page-2.png)

While we're at it, let's make a few tweaks to the rest of the sign-in flow. Change the default
page component (`com.eelchat.ui/page`) so it has the same off-white background as the landing page:

```diff
;; src/com/eelchat/ui.clj
;; ...
 (defn page [ctx & body]
   (base
    ctx
-   [:.flex-grow]
-   [:.p-3.mx-auto.max-w-screen-sm.w-full
-    (when (bound? #'csrf/*anti-forgery-token*)
-      {:hx-headers (cheshire/generate-string
-                    {:x-csrf-token csrf/*anti-forgery-token*})})
-    body]
-   [:.flex-grow]
-   [:.flex-grow]))
+   [:.bg-orange-50.flex.flex-col.flex-grow
+    [:.flex-grow]
+    [:.p-3.mx-auto.max-w-screen-sm.w-full
+     (when (bound? #'csrf/*anti-forgery-token*)
+       {:hx-headers (cheshire/generate-string
+                     {:x-csrf-token csrf/*anti-forgery-token*})})
+     body]
+    [:.flex-grow]
+    [:.flex-grow]]))
```

Update some of the wording in `com.eelchat.email`:

```diff
;; src/com/eelchat/email.clj
;; ...
 (defn signin-link [{:keys [to url user-exists]}]
-  (let [[subject action] (if user-exists
-                           [(str "Sign in to " settings/app-name) "sign in"]
-                           [(str "Sign up for " settings/app-name) "sign up"])]
-    {:to [{:email to}]
-     :subject subject
-     :html (rum/render-static-markup
-            [:html
-             [:body
-              [:p "We received a request to " action " to " settings/app-name
-               " using this email address. Click this link to " action ":"]
-              [:p [:a {:href url :target "_blank"} "Click here to " action "."]]
-              [:p "This link will expire in one hour. "
-               "If you did not request this link, you can ignore this email."]]])
-     :text (str "We received a request to " action " to " settings/app-name
-                " using this email address. Click this link to " action ":\n"
-                "\n"
-                url "\n"
-                "\n"
-                "This link will expire in one hour. If you did not request this link, "
-                "you can ignore this email.")}))
+  {:to [{:email to}]
+   :subject "Join the eelchat waitlist"
+   :html (rum/render-static-markup
+          [:html
+           [:body
+            [:p "We received a request to join " settings/app-name
+             " using this email address. Click this link to join:"]
+            [:p [:a {:href url :target "_blank"} "Join the eelchat waitlist"]]
+            [:p "This link will expire in one hour. "
+             "If you did not request this link, you can ignore this email."]]])
+   :text (str "We received a request to join " settings/app-name
+              " using this email address. Click this link to join the waitlist:\n"
+              "\n"
+              url "\n"
+              "\n"
+              "This link will expire in one hour. If you did not request this link, "
+              "you can ignore this email.")})
```

And do the same in `com.eelchat.app`:

```diff
;; src/com/eelchat/app.clj
;; ...
      [:.h-6]
-     [:div "Nothing here yet."])))
+     [:div "Thanks for joining the waitlist. "
+      "We'll let you know when eelchat is ready to use."])))
```
