(ns com.biffweb.demo.lib.ui
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [com.biffweb.datastar :as biff.datastar]
            [lambdaisland.hiccup :as hiccup]
            [ring.util.response :as ring-response]))

(def ^:private datastar-script-url
  "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.1/bundles/datastar.js")

(defn csrf-field [{:keys [anti-forgery-token]}]
  [:input {:type  "hidden"
           :name  "__anti-forgery-token"
           :value anti-forgery-token}])

(defn css-path []
  (if-some [last-modified
            (some-> (io/resource "public/css/main.css")
                    ring-response/resource-data
                    :last-modified
                    (.getTime))]
    (str "/css/main.css?t=" last-modified)
    "/css/main.css"))

(defn html-response [body]
  {:status  200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (hiccup/render body)})

(defn signal-patch-response [signals]
  {:status  200
   :headers {"Cache-Control" "no-store"
             "Content-Type"  "text/event-stream; charset=utf-8"}
   :body    (str "event: datastar-patch-signals\n"
                 "data: signals "
                 (json/generate-string signals)
                 "\n\n")})

(defn no-content []
  {:status 204})

(defn page
  [{:keys [title anti-forgery-token]} & body]
  (html-response
   [:html {:lang "en"}
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name    "viewport"
             :content "width=device-width, initial-scale=1"}]
     [:title title]
     [:link {:rel "icon" :href "data:,"}]
     [:link {:rel  "stylesheet"
             :href (css-path)}]
     [:script {:type "module"
               :src  datastar-script-url}]
     [:script {:type "module"}
      (biff.datastar/configure-csrf
       datastar-script-url
       anti-forgery-token)]]
    (into
     [:body.bg-slate-50.text-slate-900]
     body)]))

(defn page-title [& children]
  (into [:h1 {:class "text-3xl font-bold text-slate-950"}] children))

(defn link
  [{:as opts} & children]
  (into
   [:a (update opts :class #(str "text-blue-600 hover:text-blue-800"
                                 (when % (str " " %))))]
   children))

(defn button
  [{:as opts} & children]
  (into
   [:button (update opts :class #(str "rounded bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700"
                                      (when % (str " " %))))]
   children))
