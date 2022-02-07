(ns com.example.ui
  (:require [clojure.java.io :as io]
            [com.biffweb :as biff]))

;; todo I think something like this helps with cache-busting? If so, maybe use
;; last-modified-time as a param so we don't have to slurp the whole thing on
;; every request.
(defn css-path []
  (str "/css/main.css?a=" (hash (biff/catchall (slurp (io/resource "public/css/main.css"))))))

(defn base [opts head & body]
  (apply
    biff/base-html
    (merge #:base{:title "My Application"
                  :lang "en-US"
                  :icon "https://cdn.findka.com/glider.png"
                  :description "My Application Description"
                  :image "https://clojure.org/images/clojure-logo-120b.png"}
           opts)
    (concat [[:link {:rel "stylesheet" :href (css-path)}]
             [:script {:src "https://unpkg.com/htmx.org@1.6.1"}]
             [:script {:src "https://unpkg.com/hyperscript.org@0.9.3"}]]
            head)
    body))

(defn page [opts head & body]
  (base
    opts
    head
    [:.p-3.mx-auto.max-w-screen-sm.w-full
     body]))

(defn render-page [& args]
  (biff/render (apply page args)))
