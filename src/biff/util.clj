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
    [crypto.random :as random]
    [byte-transforms :as bt]
    [byte-streams :as bs]
    [ring.util.time :as rtime]
    [ring.util.codec :as codec]
    [ring.util.request :as request]
    [ring.middleware.anti-forgery :as anti-forgery]
    [ring.middleware.token :as token]
    [taoensso.sente :as sente]
    [taoensso.sente.server-adapters.immutant :refer [get-sch-adapter]]
    [rum.core :as rum]
    [trident.util :as u])
  (:import
    [com.auth0.jwt.algorithms Algorithm]
    [com.auth0.jwt JWT]))

(def html rum.core/render-static-markup)

(defmacro defmemo [sym ttl & forms]
  `(do
     (defn f# ~@forms)
     (def ~sym (memo/ttl f# :ttl/threshold ~ttl))))

(defn deps []
  (-> "deps.edn"
    slurp
    edn/read-string))

(defn secrets []
  (-> "secrets.edn"
    slurp
    edn/read-string))

(defn secret-key [path]
  (or
    (u/catchall (slurp path))
    (let [k-str (bs/to-string (bt/encode (random/bytes 16) :base64))]
      (io/make-parents path)
      (spit path k-str)
      k-str)))

(defn cookie-key [path]
  (-> path
    secret-key
    (bt/decode :base64)))

(defn encode-jwt [claims {:keys [secret alg]}]
  ; todo add more algorithms
  (let [alg (case alg
              :HS256 (Algorithm/HMAC256 secret))]
    (->
      (reduce (fn [token [k v]]
                (.withClaim token (name k) v))
        (JWT/create)
        claims)
      (.sign alg))))

(def decode-jwt token/decode)

(defn write-deps! [deps]
  (-> deps
    u/pprint
    with-out-str
    (#(spit "deps.edn" %))))

(defn update-deps! [f & args]
  (write-deps! (apply f (deps) args)))

(defn wrap-authorize [handler]
  (anti-forgery/wrap-anti-forgery
    (fn [{:keys [uri] :as req}]
      (if (get-in req [:session :admin])
        (handler req)
        {:status 302
         :headers {"Location" (str "/biff/auth?next=" (url/url-encode uri))}
         :body ""}))))

(defn copy-resources [src-root dest-root]
  (let [resource-root (io/resource src-root)
        files (->> resource-root
                io/as-file
                file-seq
                (filter #(.isFile %))
                (map #(subs (.getPath %) (count (.getPath resource-root)))))]
    (doseq [f files
            :let [src (str (.getPath resource-root) f)
                  dest (str dest-root f)]]
      (io/make-parents dest)
      (io/copy (io/file src) (io/file dest)))))

(defn file-response [req file]
  (when (.isFile file)
    (head/head-response
      (u/assoc-some
        {:body file
         :status 200
         :headers/Content-Length (.length file)
         :headers/Last-Modified (rtime/format-date (rio/last-modified-date file))}
        :headers/Content-Type (when (str/ends-with? (.getPath file) ".html")
                                "text/html"))
      req)))

(defn file-handler [root]
  (fn [{:keys [request-method] :as req}]
    (when (#{:get :head} request-method)
      (let [path (str root (codec/url-decode (request/path-info req)))
            path (cond-> path
                   (.isDirectory (io/file path)) (str/replace-first #"/?$" "/index.html"))
            file (io/file path)]
        (file-response req file)))))

(defn ring-settings [debug cookie-key]
  (-> (if debug
        rd/site-defaults
        rd/secure-site-defaults)
    (update :session merge {:store (cookie/cookie-store {:key cookie-key})
                            :cookie-name "ring-session"})
    (update :security merge {:anti-forgery false
                             :ssl-redirect false})
    (assoc :static false)))

(defn nest-keys [m ks]
  (let [ks (set ks)]
    (reduce (fn [resp [k v]]
              (let [nested-k (keyword (namespace k))]
                (if (ks nested-k)
                  (-> resp
                    (update nested-k assoc (name k) v)
                    (dissoc k))
                  resp)))
    m
    m)))

(defn nice-response [resp]
  (when resp
    (-> {:body "" :status 200}
      (merge resp)
      (nest-keys [:headers :cookies]))))

(defn wrap-nice-response [handler]
  (comp nice-response handler))

(defn make-handler [{:keys [root debug routes cookie-path default-routes]
                     ckey :cookie-key}]
  (let [cookie-key (or ckey (some-> cookie-path cookie-key))
        not-found #(file-response % (io/file (str root "/404.html")))
        default-handlers (->> [(when debug
                                 (file-handler "www-dev"))
                               (when (and debug root)
                                 (file-handler root))
                               (reitit/create-default-handler
                                 {:not-found not-found})]
                           (concat default-routes)
                           (filter some?))]
    (->
      (reitit/ring-handler
        (reitit/router routes)
        (apply reitit/routes default-handlers))
      wrap-nice-response
      (rd/wrap-defaults (ring-settings debug cookie-key)))))

; you could say that rum is one of our main exports
(defn export-rum [pages dir]
  (doseq [[path form] pages
          :let [full-path (cond-> (str dir path)
                            (str/ends-with? path "/") (str "index.html"))]]
    (io/make-parents full-path)
    (spit full-path (rum/render-static-markup form))))

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

(defn wrap-sente-handler [handler]
  (fn [{:keys [?data ?reply-fn] :as event}]
    (some->>
      (with-out-str
        (let [event (merge event (get-in event [:ring-req :session]))
              response (try
                         (handler event ?data)
                         (catch Exception e
                           (.printStackTrace e)
                           ::exception))]
          (when ?reply-fn
            (?reply-fn response))))
      not-empty
      (.print System/out))))

(defn init-sente [{:keys [route-name handler]}]
  (let [{:keys [ch-recv send-fn connected-uids
                ajax-post-fn ajax-get-or-ws-handshake-fn]}
        (sente/make-channel-socket! (get-sch-adapter) {:user-id-fn :client-id})]
    {:reitit-route ["/chsk" {:get ajax-get-or-ws-handshake-fn
                             :post ajax-post-fn
                             :middleware [anti-forgery/wrap-anti-forgery]
                             :name route-name}]
     :start-router #(sente/start-server-chsk-router! ch-recv
                      (wrap-sente-handler handler))
     :api-send send-fn
     :connected-uids connected-uids}))

(defmacro defkeys [m & syms]
  `(let [{:keys [~@syms]} ~m]
     ~@(for [s syms]
         `(def ~s ~s))))

(defn merge-safe [& ms]
  (if-some [shared-keys (not-empty (apply set/intersection (comp set keys) ms))]
    (throw (ex-info "Attempted to merge duplicate keys"
             {:keys shared-keys}))
    (apply merge ms)))
