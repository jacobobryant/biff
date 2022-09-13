(ns com.example.ui
  (:require [clojure.java.io :as io]
            [com.biffweb :as biff]))

(defn css-path []
  (if-some [f (io/file (io/resource "public/css/main.css"))]
    (str "/css/main.css?t=" (.lastModified f))
    "/css/main.css"))

(defn include-livejs? [opts]
  (:com.example/include-livejs opts))

(defn base [opts & body]
  (apply
   biff/base-html
   (-> opts
       (merge #:base{:title "My Application"
                     :lang "en-US"
                     :icon "/img/glider.png"
                     :description "My Application Description"
                     :image "https://clojure.org/images/clojure-logo-120b.png"})
       (update :base/head (fn [head]
                            (concat [[:link {:rel "stylesheet" :href (css-path)}]
                                     [:script {:src "https://unpkg.com/htmx.org@1.6.1"}]
                                     [:script {:src "https://unpkg.com/hyperscript.org@0.9.3"}]
                                     (when (include-livejs? opts)
                                       [:script {:src "/js/live.js"}])]
                                    head))))
   body))

(defn page [opts & body]
  (base
   opts
   [:.p-3.mx-auto.max-w-screen-sm.w-full
    body]))
