(ns {{parent-ns}}.env
  (:require [biff.util :as bu]))

(def env-keys
  ; env key | clj key | coerce fn
  [["CRUX_DIR"           :biff.crux/dir]
   ["CRUX_TOPOLOGY"      :biff.crux/topology keyword]
   ["CRUX_JDBC_DBNAME"   :biff.crux.jdbc/dbname]
   ["CRUX_JDBC_USER"     :biff.crux.jdbc/user]
   ["CRUX_JDBC_PASSWORD" :biff.crux.jdbc/password]
   ["CRUX_JDBC_HOST"     :biff.crux.jdbc/host]
   ["CRUX_JDBC_PORT"     :biff.crux.jdbc/port #(Long/parseLong %)]
   ["HOST"               :biff/host]
   ["PORT"               :biff/port #(Long/parseLong %)]
   ["BASE_URL"           :biff/base-url]
   ["NREPL_PORT"         :biff.nrepl/port #(Long/parseLong %)]
   ["MAILGUN_KEY"        :mailgun/api-key]
   ["MAILGUN_ENDPOINT"   :mailgun/endpoint]
   ["MAILGUN_FROM"       :mailgun/from]
   ["RECAPTCHA_SECRET"   :recaptcha/secret-key]
   ["JWT_SECRET"         :biff.auth/jwt-secret]
   ["COOKIE_SECRET"      :biff.middleware/cookie-secret]
   ["SECURE_COOKIES"     :biff.middleware/secure-cookies #(= "true" %)]
   ["REITIT_MODE"        :biff.reitit/mode keyword]])

(defn use-env [sys]
  (merge sys (bu/read-env env-keys)))
