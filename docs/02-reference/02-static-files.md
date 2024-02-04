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

(def module
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

In addition, any files you put in `resources/public/` will be served, so you can
use that folder for logo images, plain JavaScript files, etc.

See also:

 - [`export-rum`](/docs/api/rum#export-rum)
 - [Rum documentation](https://github.com/tonsky/rum)
 - [Tailwind documentation](https://tailwindcss.com/)
