(ns com.biffweb.authenticate.impl.routes
  (:require
   [clojure.string :as str]))

(defn append-query-params [path params-str]
  (if (str/includes? path "?")
    (str path "&" params-str)
    (str path "?" params-str)))

(defmacro defpath [sym path]
  `(defn ~sym
     ([] ~path)
     ([params-str#]
      (append-query-params ~path params-str#))))

(defpath send-code           "/_biff/auth/send-code")
(defpath send-link           "/_biff/auth/send-link")
(defpath verify-code         "/_biff/auth/verify-code")
(defpath verify-link-confirm "/_biff/auth/verify-link-confirm")

(defn verify-link
  ([]
   "/_biff/auth/verify-link/:payload")
  ([payload]
   (str/replace (verify-link) #":payload" payload))
  ([base-url payload]
   (str base-url (verify-link payload))))

(def signout-link        "/_biff/auth/signout")
(def default-code-page        "/signin")
(def default-link-page        "/signup")
(def default-verify-link-page "/signup/verify")
(def default-app-page         "/app")
