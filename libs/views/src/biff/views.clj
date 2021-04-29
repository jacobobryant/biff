(ns biff.views
  (:require [rum.core :as rum]))

(defn render
  ([f opts m]
   (merge
     {:status 200
      :headers/Content-Type "text/html; charset=utf-8"
      :body (rum/render-static-markup (f opts))}
     m))
  ([f opts] (render f opts nil)))

(defn unsafe
  ([m html]
   (merge m {:dangerouslySetInnerHTML {:__html html}}))
  ([html] (unsafe {} html)))

(def mdash [:span (unsafe "&mdash;")])

(def endash [:span (unsafe "&#8211;")])

(def nbsp [:span (unsafe "&nbsp;")])

(defn g-fonts [families]
  [:link {:href (apply str "https://fonts.googleapis.com/css2?display=swap"
                  (for [f families]
                    (str "&family=" f)))
          :rel "stylesheet"}])

(defn base [{:base/keys [title
                         description
                         lang
                         image
                         icon
                         url
                         canonical
                         font-families
                         head]}
            & contents]
  [:html
   {:lang lang
    :style {:min-height "100%"
            :height "auto"}}
   [:head
    [:title title]
    [:meta {:name "description" :content description}]
    [:meta {:content title :property "og:title"}]
    [:meta {:content description :property "og:description"}]
    (when image
      [:meta {:content image :property "og:image"}])
    [:meta {:content "summary" :name "twitter:card"}]
    (when url
      [:meta {:content url :property "og:url"}])
    (when-some [url (or canonical url)]
      [:link {:ref "canonical" :href url}])
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    (when icon
      [:link {:rel "icon"
              :type "image/png"
              :sizes "16x16"
              :href icon}])
    [:meta {:charset "utf-8"}]
    (some-> font-families g-fonts)
    head]
   [:body
    {:style {:position "absolute"
             :width "100%"
             :min-height "100%"
             :display "flex"
             :flex-direction "column"}}
    contents]])

(defn gap [width height]
  [:div {:style {:display "inline-block"
                 :width width
                 :height height}}])
