---
title: Make a landing page
---

[View the code for this section](https://github.com/jacobobryant/eelchat/commit/e4bb7b9f12a9b1057d02e462dee12dd8061d038d).

Now that we've stripped our app down to the essentials, let's start building it
back up. For now we'll just do a little branding. The app won't do anything
yet, but at least you'll have a landing page. You can start collecting emails
for a waitlist!

Let's go to [localhost:8080](http://localhost:8080) once more. If you're signed
in, hit the `Sign out` button. You should be redirected to the landing page:

![A screenshot of the landing page before any changes are made](/img/tutorial/landing-page-1.png)

We'll update the site metadata first. Go to `com.eelchat.ui` and change the
application name from `My Application` to `eelchat`.

```diff
;; src/com/eelchat/ui.clj
;; ...
   (apply
    biff/base-html
    (-> opts
-       (merge #:base{:title "My Application"
+       (merge #:base{:title "eelchat"
                      :lang "en-US"
                      :icon "/img/glider.png"
-                     :description "My Application Description"
+                     :description "The world's finest discussion platform."
                      :image "https://clojure.org/images/clojure-logo-120b.png"})
        (update :base/head (fn [head]
                             (concat [[:link {:rel "stylesheet" :href (css-path)}]
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
    (-> opts
        (merge #:base{:title "eelchat"
                      :lang "en-US"
-                     :icon "/img/glider.png"
                      :description "The world's finest discussion platform."
-                     :image "https://clojure.org/images/clojure-logo-120b.png"})
+                     :image "/img/logo.png"})
        (update :base/head (fn [head]
                             (concat [[:link {:rel "stylesheet" :href (css-path)}]
                                      [:script {:src "https://unpkg.com/htmx.org@1.6.1"}]
-                                     [:script {:src "https://unpkg.com/hyperscript.org@0.9.3"}]]
+                                     [:script {:src "https://unpkg.com/hyperscript.org@0.9.3"}]
+                                     [:link {:href "/apple-touch-icon.png", :sizes "180x180", :rel "apple-touch-icon"}]
+                                     [:link {:href "/favicon-32x32.png", :sizes "32x32", :type "image/png", :rel "icon"}]
+                                     [:link {:href "/favicon-16x16.png", :sizes "16x16", :type "image/png", :rel "icon"}]
+                                     [:link {:href "/site.webmanifest", :rel "manifest"}]
+                                     [:link {:color "#5bbad5", :href "/safari-pinned-tab.svg", :rel "mask-icon"}]
+                                     [:meta {:content "#da532c", :name "msapplication-TileColor"}]
+                                     [:meta {:content "#0d9488", :name "theme-color"}]]
                                     head))))
    body))
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
(RIP—use [html2hicuup](http://html2hiccup.buttercloud.com/) instead)
for conversion—the resulting code is shown above in the `:base/head` section.

Now go to `com.eelchat.feat.home`. Replace the `recaptcha-disclosure` and `signin-form` functions with
the following implementations:

```clojure
;; src/com/eelchat/feat/home.clj
;; ...
(def recaptcha-disclosure
  [:span "This site is protected by reCAPTCHA and the Google "
   [:a.text-teal-600.hover:underline
    {:href "https://policies.google.com/privacy"
     :target "_blank"}
    "Privacy Policy"] " and "
   [:a.text-teal-600.hover:underline
    {:href "https://policies.google.com/terms"
     :target "_blank"}
    "Terms of Service"] " apply."])

(defn signin-form [{:keys [recaptcha/site-key] :as sys}]
  (biff/form
   {:id "signin-form"
    :action "/auth/send"
    :class "sm:max-w-xs w-full"}
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
    (merge
     (when (util/email-signin-enabled? sys)
       {:data-sitekey site-key
        :data-callback "onSubscribe"
        :data-action "subscribe"})
     {:type "submit"
      :class '[bg-teal-600
               hover:bg-teal-800
               text-white
               py-2
               px-4
               rounded
               w-full
               g-recaptcha]})
    "Join the waitlist"]))
```

And then replace the `home` function with this:

```clojure
;; src/com/eelchat/feat/home.clj
;; ...
(defn home [sys]
  (ui/base
   {:base/head (when (util/email-signin-enabled? sys)
                 recaptcha-scripts)}
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
    (signin-form sys)
    [:.h-12 {:class "grow-[2]"}]
    [:.text-sm recaptcha-disclosure]
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
 (defn page [opts & body]
   (base
    opts
-   [:.p-3.mx-auto.max-w-screen-sm.w-full
-    body]))
+   [:.bg-orange-50.flex.flex-col.flex-grow
+    [:.grow]
+    [:.p-3.mx-auto.max-w-screen-sm.w-full
+     body]
+    [:div {:class "grow-[2]"}]]))
```

Update some of the wording in `com.eelchat.auth`:

```diff
;; src/com/eelchat/feat/auth.clj
;; ...
 (defn signin-template [{:keys [to url]}]
   {:to to
-   :subject "Sign in to My Application"
+   :subject "Join the eelchat waitlist"
    :html-body (rum/render-static-markup
                [:html
                 [:body
-                 [:p "We received a request to sign in to My Application using this email address."]
-                 [:p [:a {:href url :target "_blank"} "Click here to sign in."]]
+                 [:p "We received a request to join eelchat using this email address."]
+                 [:p [:a {:href url :target "_blank"} "Click here to join the waitlist."]]
                  [:p "If you did not request this link, you can ignore this email."]]])
    :message-stream "outbound"})
 
;; ...
 (def signin-sent
   (ui/page
    {}
-   [:div "We've sent a sign-in link to your email address. Please check your inbox."]))
+   [:div "We've sent a confirmation link to your email address. Please check your inbox."]))
 
 (def signin-fail
   (ui/page
```

And do the same in `com.eelchat.feat.app`:

```diff
;; src/com/eelchat/feat/app.clj
;; ...
         "Sign out"])
       "."]
      [:.h-6]
-     [:div "Nothing here yet."])))
+     [:div "Thanks for joining the waitlist. "
+      "We'll let you know when eelchat is ready to use."])))
 
 (def features
   {:routes ["/app" {:middleware [mid/wrap-signed-in]}
```
