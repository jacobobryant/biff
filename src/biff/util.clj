(ns biff.util
  (:require
    [cemerick.url :as url]
    [clojure.set :as set]
    [ring.middleware.defaults :as rd]
    [ring.middleware.session.cookie :as cookie]
    [clojure.edn :as edn]
    [clojure.core.memoize :as memo]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [reitit.ring :as reitit]
    [ring.middleware.not-modified :refer [wrap-not-modified]]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.middleware.head :as head]
    [ring.util.io :as rio]
    [ring.util.time :as rtime]
    [ring.util.codec :as codec]
    [ring.util.request :as request]
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
       :headers {"Location" (str "/biff/auth?next=" (url/url-encode uri))}
       :body ""})))

(defn copy-resources [dir]
  (let [resource-root (io/resource dir)
        files (->> resource-root
                io/as-file
                file-seq
                (filter #(.isFile %))
                (map #(subs (.getPath %) (count (.getPath resource-root)))))]
    (doseq [f files
            :let [src (str (.getPath resource-root) f)
                  dest (str dir f)
                  src-file (io/file src)]]
      (io/make-parents dest)
      (io/copy (io/file src) (io/file dest)))))

(defn wrap-files [handler root]
  (fn [{:keys [request-method] :as req}]
    (or
      (when (#{:get :head} request-method)
        (let [path (str root (codec/url-decode (request/path-info req)))
              path (cond-> path
                     (.isDirectory (io/file path)) (str/replace-first #"/?$" "/index.html"))
              file (io/file path)
              handler (-> handler
                        wrap-content-type
                        wrap-not-modified)]
          (when (.isFile file)
            (head/head-response
              {:body file
               :headers (cond-> {"Content-Length" (.length file)
                                 "Last-Modified" (rtime/format-date (rio/last-modified-date file))}
                          (str/ends-with? path ".html") (assoc "Content-Type" "text/html"))}
              req))))
      (handler req))))

(defn ring-settings [debug secret-key]
  (-> (if debug
        rd/site-defaults
        rd/secure-site-defaults)
    (assoc-in [:session :store] (cookie/cookie-store {:key secret-key}))
    (assoc-in [:security :anti-forgery] false)
    (assoc :static false)
    (assoc-in [:security :ssl-redirect] false)))

(defn make-app-handler [{:keys [root debug routes secret-key]}]
  (cond-> (reitit/ring-handler (reitit/router routes))
    debug (wrap-files root)
    true (rd/wrap-defaults (ring-settings debug secret-key))))

(defn root [config nspace]
  (-> config
    :main
    :biff.http/host->ns
    set/map-invert
    (get nspace)
    (#(str "www/" %))))

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
