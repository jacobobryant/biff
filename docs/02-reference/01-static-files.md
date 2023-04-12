---
title: Static Files
---

You can create static HTML files by supplying a map from paths to
[Rum](https://github.com/tonsky/rum) data structures. In
`com.example.app`, there's a static About page:


```clojure
(def about-page
  (ui/page
   {:base/title (str "About " settings/app-name)}
   [:p "This app was made with "
    [:a.link {:href "https://biffweb.com"} "Biff"] "."]))

(def plugin
  {:static {"/about/" about-page}
   ...})
```

The map values (`about-page` in this case) are passed to
`rum.core/render-static-markup` and written to the path you specify. If the
path ends in a `/`, then `index.html` will be appended to it.

You can use Tailwind CSS to style your HTML:

```clojure
[:button.bg-blue-500.text-white.text-center.py-2.px-4
 {:type "submit"}
 "Sign in"]
```

The HTML and Tailwind CSS files will be regenerated whenever you save a file.

In addition, any files you put in `resources/public/` will be served, so you
use that folder for logo images, plain JavaScript files, etc.

See also:

 - [Rum documentation](https://github.com/tonsky/rum)
 - [Tailwind documentation](https://tailwindcss.com/)
 - [`export-rum`](https://github.com/jacobobryant/biff/blob/bdd1bd81d95ee36c615495a946c7c1aa92d19e2e/src/com/biffweb/impl/rum.clj#L105)
