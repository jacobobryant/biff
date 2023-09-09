(ns com.biffweb.impl.util.s3
  (:require [com.biffweb.impl.util :as bu]
            [buddy.core.mac :as mac]
            [clj-http.client :as http]
            [clojure.string :as str]))

(defn hmac-sha1-base64 [secret s]
  (-> (mac/hash s {:key secret :alg :hmac+sha1})
      bu/base64-encode))

(defn md5-base64 [body]
  (with-open [f (cond
                  (string? body) (java.io.ByteArrayInputStream. (.getBytes body))
                  :else (java.io.FileInputStream. body))]
    (let [buffer (byte-array 1024)
          md (java.security.MessageDigest/getInstance "MD5")]
      (loop [nread (.read f buffer)]
        (if (pos? nread)
          (do
            (.update md buffer 0 nread)
            (recur (.read f buffer)))
          (bu/base64-encode (.digest md)))))))

(defn body->bytes [body]
  (cond
   (string? body) (.getBytes body)
   :else (let [out (byte-array (.length body))]
           (with-open [in (java.io.FileInputStream. body)]
             (.read in out)
             out))))

(defn s3-request [{:keys [biff/secret]
                   :biff.s3/keys [origin
                                  access-key
                                  bucket
                                  key
                                  method
                                  headers
                                  body]}]
  ;; See https://docs.aws.amazon.com/AmazonS3/latest/userguide/RESTAuthentication.html
  (let [date (.format (doto (new java.text.SimpleDateFormat "EEE, dd MMM yyyy HH:mm:ss Z")
                        (.setTimeZone (java.util.TimeZone/getTimeZone "UTC")))
                      (java.util.Date.))
        path (str "/" bucket "/" key)
        md5 (some-> body md5-base64)
        headers' (->> headers
                      (mapv (fn [[k v]]
                              [(str/trim (str/lower-case k)) (str/trim v)]))
                      (into {}))
        content-type (get headers' "content-type")
        headers' (->> headers'
                      (filterv (fn [[k v]]
                                 (str/starts-with? k "x-amz-")))
                      (sort-by first)
                      (mapv (fn [[k v]]
                              (str k ":" v "\n")))
                      (apply str))
        string-to-sign (str method "\n" md5 "\n" content-type "\n" date "\n" headers' path)
        signature (hmac-sha1-base64 (secret :biff.s3/secret-key) string-to-sign)
        auth (str "AWS " access-key ":" signature)
        s3-opts {:method method
                 :url (str origin path)
                 :headers (merge {"Authorization" auth
                                  "Date" date
                                  "Content-MD5" md5}
                                 headers)
                 :body (some-> body body->bytes)}]
    (http/request s3-opts)))
