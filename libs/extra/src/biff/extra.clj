(ns biff.extra
  (:require
    [buddy.sign.nonce :as nonce]
    [buddy.sign.jwt :as jwt]
    [clj-http.client :as http]
    [biff.util :as bu]
    [lambdaisland.uri :as uri]))

(defn jwt-encrypt [claims secret]
  (jwt/encrypt
    (-> claims
      (assoc :exp (bu/add-seconds (java.util.Date.) (:exp-in claims)))
      (dissoc :exp-in))
    (bu/base64-decode secret)
    {:alg :a256kw :enc :a128gcm}))

(defn jwt-decrypt [token secret]
  (bu/catchall
    (jwt/decrypt
      token
      (bu/base64-decode secret)
      {:alg :a256kw :enc :a128gcm})))

(defn generate-secret [length]
  (bu/base64-encode (nonce/random-bytes length)))

(defn assoc-url [url & kvs]
  (str (apply uri/assoc-query url kvs)))

(defn send-mailgun [{:mailgun/keys [api-key endpoint from]} opts]
  (try
    (http/post endpoint
      {:basic-auth ["api" api-key]
       :form-params (merge {:from from} opts)})
    true
    (catch Exception e
      (println "send-mailgun failed:" (:body (ex-data e)))
      false)))
