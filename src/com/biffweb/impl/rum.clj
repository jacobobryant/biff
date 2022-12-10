(ns com.biffweb.impl.rum
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [com.biffweb.impl.util :as util]
            [ring.middleware.anti-forgery :as anti-forgery]
            [rum.core :as rum]))

(defn render [body]
  {:status 200
   :headers {"content-type" "text/html; charset=utf-8"}
   :body (str "<!DOCTYPE html>\n" (rum/render-static-markup body))})

(defn unsafe [html]
  {:dangerouslySetInnerHTML {:__html html}})

(def emdash [:span (unsafe "&mdash;")])

(def endash [:span (unsafe "&#8211;")])

(def nbsp [:span (unsafe "&nbsp;")])

(defn g-fonts
  [families]
  [:link {:href (apply str "https://fonts.googleapis.com/css2?display=swap"
                       (for [f families]
                         (str "&family=" f)))
          :rel "stylesheet"}])

(defn base-html
  [{:base/keys [title
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
      [:<>
       [:meta {:content image :property "og:image"}]
       [:meta {:content "summary_large_image" :name "twitter:card"}]])
    (when-some [url (or url canonical)]
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
    (when (not-empty font-families)
      [:<>
       [:link {:href "https://fonts.googleapis.com", :rel "preconnect"}]
       [:link {:crossorigin "crossorigin",
               :href "https://fonts.gstatic.com",
               :rel "preconnect"}]
       (g-fonts font-families)])
    (into [:<>] head)]
   [:body
    {:style {:position "absolute"
             :width "100%"
             :min-height "100%"
             :display "flex"
             :flex-direction "column"}}
    contents]])

(defn form
  [{:keys [hidden] :as opts} & body]
  [:form (-> (merge {:method "post"} opts)
             (dissoc :hidden)
             (assoc-in [:style :margin-bottom] 0))
   (for [[k v] (util/assoc-some hidden "__anti-forgery-token" anti-forgery/*anti-forgery-token*)]
     [:input {:type "hidden"
              :name k
              :value v}])
   body])

;; you could say that rum is one of our main exports
(defn export-rum
  [pages dir]
  (doseq [[path rum] pages
          :let [full-path (cond-> (str dir path)
                            (str/ends-with? path "/") (str "index.html"))]]
    (io/make-parents full-path)
    (spit full-path (str "<!DOCTYPE html>\n" (rum/render-static-markup rum)))))
