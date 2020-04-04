(ns nimbus.util
  (:require
    [cemerick.url :as url]
    [clojure.edn :as edn]
    [clojure.core.memoize :as memo]
    [ring.middleware.anti-forgery :as anti-forgery]
    [rum.core :as rum]
    [trident.util :as u]))

(defmacro defmemo [sym ttl & forms]
  `(do
     (defn f# ~@forms)
     (def ~sym (memo/ttl f# :ttl/threshold ~ttl))))

(defn deps []
  (-> "deps.edn"
    slurp
    edn/read-string))

(defn write-deps! [deps]
  (-> deps
    u/pprint
    with-out-str
    (#(spit "deps.edn" %))))

(defn update-deps! [f & args]
  (write-deps! (apply f (deps) args)))

(defn wrap-authorize [handler]
  (fn [{:keys [uri] :as req}]
    (if (get-in req [:session :admin])
      (handler req)
      {:status 302
       :headers {"Location" (str "/nimbus/auth?next=" (url/url-encode uri))}
       :body ""})))

(defn render [component opts]
  {:status 200
   :body (rum/render-static-markup (component opts))
   :headers {"Content-Type" "text/html"}})

(def html-opts
  {:lang "en-US"
   :style {:min-height "100%"}})

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
   [:a {:href "/"} [:.navbar-brand "Nimbus"]]
   [:.flex-grow-1]
   contents])

(defn unsafe [m html]
  (assoc m :dangerouslySetInnerHTML {:__html html}))

(defn csrf []
  [:input#__anti-forgery-token
   {:name "__anti-forgery-token"
    :type "hidden"
    :value (force anti-forgery/*anti-forgery-token*)}])
