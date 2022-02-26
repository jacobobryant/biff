(ns biff.rum
  "Convenience functions for Rum (https://github.com/tonsky/rum)."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :refer [postwalk]]
            [rum.core :as rum]))

(defn render
  "Given a Rum component f, returns a 200 response.

  m: If provided, merges this into the response."
  ([f opts m]
   (merge (render (f opts)) m))
  ([f opts] (render f opts nil))
  ([body]
   {:status 200
    :headers/Content-Type "text/html; charset=utf-8"
    :body (rum/render-static-markup body)}))

(defn unsafe
  "Return a map with :dangerouslySetInnerHTML, optionally merged into m."
  ([m html]
   (merge m {:dangerouslySetInnerHTML {:__html html}}))
  ([html] (unsafe {} html)))

(def mdash [:span (unsafe "&mdash;")])

(def endash [:span (unsafe "&#8211;")])

(def nbsp [:span (unsafe "&nbsp;")])

(defn g-fonts
  "Returns a link element for requesting families from Google fonts."
  [families]
  [:link {:href (apply str "https://fonts.googleapis.com/css2?display=swap"
                  (for [f families]
                    (str "&family=" f)))
          :rel "stylesheet"}])

(defn base
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
    (when (not-empty font-families)
      (list
        [:link {:href "https://fonts.googleapis.com", :rel "preconnect"}]
        [:link {:crossorigin "crossorigin",
                :href "https://fonts.gstatic.com",
                :rel "preconnect"}]
        (some-> font-families g-fonts)))
    head]
   [:body
    {:style {:position "absolute"
             :width "100%"
             :min-height "100%"
             :display "flex"
             :flex-direction "column"}}
    contents]])

(defn form
  "Returns a form.

  hidden: A map from names to values, which will be converted to hidden input
          fields.
  opts:   Options for the :form element (with hidden removed)"
  [{:keys [hidden] :as opts} & body]
  [:form (merge
           {:method "post"}
           (dissoc opts :hidden))
   (for [[k v] (:hidden opts)]
     [:input {:type "hidden"
              :name k
              :value v}])
   body])

; you could say that rum is one of our main exports
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
    (spit full-path (str "<!DOCTYPE html>\n" (rum/render-static-markup rum)))))


(defmacro defatoms
  "Convenience macro for defining multiple atoms.

  For example:

  (defatoms
    foo 1
    bar 2)

  => (def foo (atom 1))
     (def bar (atom 2))"
  [& kvs]
  `(do
     ~@(for [[k v] (partition 2 kvs)]
         `(defonce ~k (atom ~v)))))

(defn- cardinality-many? [x]
  (boolean
    (some #(% x)
      [list?
       #(instance? clojure.lang.IMapEntry %)
       seq?
       #(instance? clojure.lang.IRecord %)
       coll?])))

(defn- postwalk-reduce [f acc x]
  (f
   (if (cardinality-many? x)
     (reduce (partial postwalk-reduce f) acc x)
     acc)
   x))

(defn- deref-form? [x]
  (and
    (list? x)
    (= 2 (count x))
    (= 'clojure.core/deref (first x))))

(defn- pred-> [x f g]
  (if (f x) (g x) x))

(defmacro defderivations
  "A convenience macro for rum.core/derived-atom.

  Like [[defatoms]], but anything preceded by @ is watched for changes, and the
  defined atoms will be kept up-to-date. Atoms are defined with defonce, so if
  you change one of the definitions, you'll need to refresh the browser window
  for it to take effect.

  For example:

  (def foo (atom 3))

  (defderivations
    bar (+ @foo 2)
    baz (* @foo @bar))

  @bar => 5
  @baz => 15
  (reset! foo 4)
  @bar => 6
  @baz => 24"
  [& kvs]
  `(do ~@(for [[sym form] (partition 2 kvs)
               :let [deps (->> form
                            (postwalk-reduce
                              (fn [deps x]
                                (if (deref-form? x)
                                  (conj deps (second x))
                                  deps))
                              [])
                            distinct
                            vec)
                     form (postwalk #(pred-> % deref-form? second) form)
                     k (java.util.UUID/randomUUID)]]
           `(defonce ~sym (rum.core/derived-atom ~deps ~k
                            (fn ~deps ~form))))))
