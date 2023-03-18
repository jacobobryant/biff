---
title: Static Files
---

You can create static HTML files by supplying a map from paths to
[Rum](https://github.com/tonsky/rum) data structures. In
`com.example.feat.auth`, we define two static pages, either of which is shown
after you request a sign-in link:


```clojure
(def signin-sent
  (ui/page
    {}
    [:div
     "The sign-in link was printed to the console. If you add an API "
     "key for MailerSend, the link will be emailed to you instead."]))

(def signin-fail
  (ui/page
    {}
    [:div
     "Your sign-in request failed. There are several possible reasons:"]
    [:ul
     [:li "You opened the sign-in link on a different device "
      "or browser than the one you requested it on."]
     [:li "We're not sure you're a human."]
     [:li "We think your email address is invalid or high risk."]
     [:li "We tried to email the link to you, but it didn't work."]]))

(def features
  {:routes ...
   :static {"/auth/sent/" signin-sent
            "/auth/fail/" signin-fail}})
```

The map values (`signin-sent` and `signin-fail` in this case) are passed to
`rum.core/render-static-markup` and written to the path you specify. If the
path ends in a `/`, then `index.html` will be appended to it.

You can use Tailwind CSS to style your HTML:

```clojure
[:button.bg-blue-500.text-white.text-center.py-2.px-4
 {:type "submit"}
 "Sign in"]
```

The HTML and Tailwind CSS files will be regenerated whenever you save a file.
In addition, any files you put in `resources/public/` will be served.

See also:

 - [Rum documentation](https://github.com/tonsky/rum)
 - [Tailwind documentation](https://tailwindcss.com/)
 - [`export-rum`](https://github.com/jacobobryant/biff/blob/bdd1bd81d95ee36c615495a946c7c1aa92d19e2e/src/com/biffweb/impl/rum.clj#L105)
