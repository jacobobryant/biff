(ns com.example.views
  (:require [clojure.java.io :as io]
            [com.biffweb :as biff]))

;; todo is this necessary for cache-busting? If so, maybe use
;; last-modified-time as a param so we don't have to slurp the whole thing.
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
    (concat [[:link {:rel "stylesheet" :href (css-path)}]]
            head)
    body))
