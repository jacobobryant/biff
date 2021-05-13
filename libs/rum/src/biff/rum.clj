(ns biff.rum
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :refer [postwalk]]
            [rum.core :as rum]))

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

(defn form [opts & body]
  [:form (merge
           {:method "post"}
           (dissoc opts :hidden))
   (for [[k v] (:hidden opts)]
     [:input {:type "hidden"
              :name k
              :value v}])
   body])

(defn gap [width height]
  [:div {:style {:display "inline-block"
                 :width width
                 :height height}}])

; you could say that rum is one of our main exports
(defn export-rum [pages dir]
  (doseq [[path form] pages
          :let [full-path (cond-> (str dir path)
                            (str/ends-with? path "/") (str "index.html"))]]
    (io/make-parents full-path)
    (spit full-path (cond-> form
                      (not (string? form)) rum/render-static-markup))))


(defmacro defatoms [& kvs]
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

(defmacro defderivations [& kvs]
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
