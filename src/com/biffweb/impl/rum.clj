(ns com.biffweb.impl.rum
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [com.biffweb.impl.util :as util]
            [ring.middleware.anti-forgery :as anti-forgery]
            [rum.core :as rum :refer [defc]]))

(defn render
  "Given a Rum data structure, returns a 200 HTML response."
  [body]
  {:status 200
   :headers {"content-type" "text/html; charset=utf-8"}
   :body (rum/render-static-markup body)})

(defn unsafe [html]
  {:dangerouslySetInnerHTML {:__html html}})

(def emdash [:span (unsafe "&mdash;")])

(def endash [:span (unsafe "&#8211;")])

(def nbsp [:span (unsafe "&nbsp;")])

(defc g-fonts
  "Returns a link element for requesting families from Google fonts."
  [families]
  [:link {:href (apply str "https://fonts.googleapis.com/css2?display=swap"
                       (for [f families]
                         (str "&family=" f)))
          :rel "stylesheet"}])

(defc base
  "Wraps contents in an :html and :body element with various metadata set.

  font-families: A collection of families to request from Google fonts.
  head:          Additional Rum elements to include inside the head."
  [{:base/keys [title
                description
                lang
                image
                icon
                url
                canonical
                font-families]}
   head & body]
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
      (list
        [:link {:href "https://fonts.googleapis.com", :rel "preconnect"}]
        [:link {:crossorigin "crossorigin",
                :href "https://fonts.gstatic.com",
                :rel "preconnect"}]
        (g-fonts font-families)))
    head]
   [:body
    {:style {:position "absolute"
             :width "100%"
             :min-height "100%"
             :display "flex"
             :flex-direction "column"}}
    body]])

(defc form
  "Returns a form.

  hidden: A map from names to values, which will be converted to hidden input
          fields.
  opts:   Options for the :form element (with hidden removed)"
  [{:keys [hidden csrf-token]
    :or {csrf-token anti-forgery/*anti-forgery-token*}
    :as opts} & body]
  [:form (-> (merge {:method "post"} opts)
             (dissoc :hidden)
             (assoc-in [:style :margin-bottom] 0))
   (for [[k v] (util/assoc-some opts "__anti-forgery-token" csrf-token)]
     [:input {:type "hidden"
              :name k
              :value v}])
   body])

;; you could say that rum is one of our main exports
(defn export-rum
  "Generate HTML files and write them to a directory.

  pages: A map from paths to Rum data structures, e.g.
         {\"/\" [:div \"hello\"]}. Paths that end in / will have index.html
         appended to them.
  dir:   A path to the root directory where the files should be saved."
  [pages dir]
  (doseq [[path rum] pages
          :let [full-path (cond-> (str dir path)
                            (str/ends-with? path "/") (str "index.html"))]]
    (io/make-parents full-path)
    (spit full-path (rum/render-static-markup rum))))
