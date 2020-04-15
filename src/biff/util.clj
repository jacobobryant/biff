(ns biff.util
  (:require
    [crux.api :as crux]
    [clojure.spec.alpha :as s]
    [clojure.core.async :as async :refer [close! >! <! go go-loop chan put!]]
    [clojure.walk :as walk]
    [cemerick.url :as url]
    [clojure.set :as set]
    [ring.middleware.defaults :as rd]
    [ring.middleware.session.cookie :as cookie]
    [clojure.edn :as edn]
    [clojure.core.memoize :as memo]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [muuntaja.middleware :as muuntaja]
    [reitit.ring :as reitit]
    [ring.middleware.not-modified :refer [wrap-not-modified]]
    [ring.middleware.content-type :refer [wrap-content-type]]
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

; trident.util

(defmacro defmemo [sym ttl & forms]
  `(do
     (defn f# ~@forms)
     (def ~sym (memo/ttl f# :ttl/threshold ~ttl))))

(defn nest-string-keys [m ks]
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

(defmacro defkeys [m & syms]
  `(let [{:keys [~@syms]} ~m]
     ~@(for [s syms]
         `(def ~s ~s))))

(defn merge-safe [& ms]
  (if-some [shared-keys (not-empty (apply set/intersection (comp set keys) ms))]
    (throw (ex-info "Attempted to merge duplicate keys"
             {:keys shared-keys}))
    (apply merge ms)))

(defn only-keys [& {:keys [req opt req-un opt-un]}]
  (let [all-keys (->> (concat req-un opt-un)
                   (map (comp keyword name))
                   (concat req opt))]
    (s/and #(= % (select-keys % all-keys))
      (eval `(s/keys :req ~req :opt ~opt :req-un ~req-un :opt-un ~opt-un)))))

(defmacro sdefs [& forms]
  `(do
     ~@(for [form (partition 2 forms)]
         `(s/def ~@form))))

(defn pipe-fn [f & fs]
  (let [from (chan)
        to (chan)
        _ (async/pipeline 1 to (map (fn [{:keys [id args]}]
                                {:id id
                                 :result (apply f args)})) from)
        to (reduce (fn [from f]
                     (let [to (chan)]
                       (async/pipeline 1 to (map #(update % :result f)) from)
                       to))
             to fs)
        p (async/pub to :id)
        next-id (fn [id]
                  (if (= Long/MAX_VALUE id)
                    0
                    (inc id)))
        id (atom 0)]
    {:f (fn [& args]
          (let [id (swap! id next-id)
                ch (chan)]
            (async/sub p id ch)
            (put! from {:id id
                        :args args})
            (go
              (let [{:keys [result]} (<! ch)]
                (async/unsub p id ch)
                result))))
     :close #(close! from)}))

(defn with-defaults [m & kvs]
  (reduce (fn  [m [k v]]
            (cond-> m
              (not (contains? m k)) (assoc k v)))
    m
    (partition 2 kvs)))

; trident.rum

(def html rum.core/render-static-markup)

; trident.jwt

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

; biff.util

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

(defn write-deps! [deps]
  (-> deps
    u/pprint
    with-out-str
    (#(spit "deps.edn" %))))

(defn update-deps! [f & args]
  (write-deps! (apply f (deps) args)))

(defn root [config nspace]
  (-> config
    :main
    :biff.http/host->ns
    set/map-invert
    (get nspace)
    (#(str "www/" %))))
