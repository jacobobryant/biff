(ns biff.util.static
  (:require
    [ring.middleware.anti-forgery :as anti-forgery]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [rum.core :as rum]))

(defn copy-resources [src-root dest-root]
  (when-some [resource-root (io/resource src-root)]
    (let [files (->> resource-root
                  io/as-file
                  file-seq
                  (filter #(.isFile %))
                  (map #(subs (.getPath %) (count (.getPath resource-root)))))]
      (doseq [f files
              :let [src (str (.getPath resource-root) f)
                    dest (str dest-root f)]]
        (io/make-parents dest)
        (io/copy (io/file src) (io/file dest))))))

; you could say that rum is one of our main exports
(defn export-rum [pages dir]
  (doseq [[path form] pages
          :let [full-path (cond-> (str dir path)
                            (str/ends-with? path "/") (str "index.html"))]]
    (io/make-parents full-path)
    (spit full-path (cond-> form
                      (not (string? form)) rum/render-static-markup))))

(def html-opts
  {:lang "en-US"
   :style {:min-height "100%"}})

(defn render [component opts]
  {:status 200
   :body (rum/render-static-markup (component opts))
   :headers {"Content-Type" "text/html"}})

(def body-opts {:style {:font-family "'Helvetica Neue', Helvetica, Arial, sans-serif"}})

(defn head [{:keys [title]} & contents]
  (into
    [:head
     [:title title]
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:link
      {:crossorigin "anonymous"
       :integrity "sha384-ggOyR0iXCbMQv3Xipma34MD+dH/1fQ784/j6cY/iJTQUOhcWr7x9JvoRxT2MZw1T"
       :href "https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css"
       :rel "stylesheet"}]]
    contents))

(defn navbar [& contents]
  [:nav.navbar.navbar-light.bg-light.align-items-center
   [:a {:href "/"} [:.navbar-brand "Biff"]]
   [:.flex-grow-1]
   contents])

(defn unsafe [m html]
  (assoc m :dangerouslySetInnerHTML {:__html html}))

(defn csrf []
  [:input#__anti-forgery-token
   {:name "__anti-forgery-token"
    :type "hidden"
    :value (force anti-forgery/*anti-forgery-token*)}])
