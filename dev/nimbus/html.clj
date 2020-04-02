(ns nimbus.html
  (:require
    [clojure.java.io :as io]
    [rum.core :as rum :refer [defc]]))

(defc app []
  [:html {:lang "en-US"
          :style {:min-height "100%"}}
   [:head
    [:title "Nimbus"]
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:link
     {:crossorigin "anonymous",
      :integrity
      "sha384-ggOyR0iXCbMQv3Xipma34MD+dH/1fQ784/j6cY/iJTQUOhcWr7x9JvoRxT2MZw1T",
      :href
      "https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css",
      :rel "stylesheet"}]]
   [:body {:style {:font-family "'Helvetica Neue', Helvetica, Arial, sans-serif"}}
    [:#app
     [:.d-flex.flex-column.align-items-center.mt-4
      [:.spinner-border.text-primary
       {:role "status"}
       [:span.sr-only
        "Loading..."]]]]
    [:script {:src "/nimbus/pack/cljs/main.js"}]]])

(def pages
  {"/" app})

(defn -main []
  (doseq [[path component] pages
          :let [full-path (str "resources/public/nimbus/pack" path "index.html")]]
    (io/make-parents full-path)
    (spit full-path (rum/render-static-markup (component)))))
