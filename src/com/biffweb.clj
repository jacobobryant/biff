(ns com.biffweb
  (:require [buddy.core.nonce :as nonce]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [com.biffweb.impl.middleware :as middle]
            [com.biffweb.impl.misc :as misc]
            [com.biffweb.impl.rum :as brum]
            [com.biffweb.impl.time :as time]
            [com.biffweb.impl.util :as util]
            [com.biffweb.impl.util.ns :as ns]
            [com.biffweb.impl.util.reload :as reload]
            [com.biffweb.impl.xtdb :as bxt]))

;;;; util

(def pprint util/pprint)

(defonce system (atom nil))

(defn start-system
  "Starts a system from an initial system map.

  Stores the system in the biff.util/system atom. See
  See https://biff.findka.com/#system-composition and refresh."
  [init]
  (util/start-system system init))

(defn refresh
  "Stops the system, refreshes source files, and restarts the system.

  The system is stopped by calling all the functions in (:biff/stop
  @biff.util/system). (:after-refresh @system) is a fully-qualified symbol which
  will be resolved and called after refreshing.

  See start-system."
  []
  (util/refresh @system))

(defn use-config [sys]
  (merge sys (util/read-config (:biff/config sys))))

(defn sh
  "Runs a shell command.

  Returns the output if successful; otherwise, throws an exception."
  [& args]
  (apply util/sh args))

(defn safe-merge [& ms]
  (apply util/safe-merge ms))

(defn normalize-email [email]
  (some-> email str/trim str/lower-case not-empty))

(defn use-when [f & components]
  (apply util/use-when f components))

(defn sha256 [string]
  (util/sha256 string))

(defn base64-encode [bs]
  (util/base64-encode bs))

(defn base64-decode [s]
  (util/base64-decode s))

(defn anomaly? [x]
  (util/anomaly? x))

(defn anom [category & [message & [opts]]]
  (util/anom category message opts))

(defn select-ns-as [m ns-from ns-to]
  (ns/select-ns-as m ns-from ns-to))

(defmacro catchall [& body]
  `(try ~@body (catch Exception ~'_ nil)))

(defmacro letd [bindings & body]
  (apply util/letd* bindings body))

(defmacro fix-print [& body]
  (apply util/fix-print* body))

(defn eval-files! [{:keys [biff/eval-paths]
                    :or {eval-paths ["src"]}}]
  (swap! reload/global-tracker reload/refresh eval-paths)
  nil)

;;;; misc

(defn use-hawk
  "A Biff component for Hawk. See https://github.com/wkf/hawk.

  on-save:       A single-argument function to call whenever a file is saved.
                 Receives the system map as a parameter. The function is called
                 no more than once every 500 milliseconds.
  paths:         A collection of root directories to monitor for file changes.
  exts:          If exts is non-empty, files that don't end in one of the
                 extensions will be ignored."
  [{:biff.hawk/keys [on-save exts paths]
    :or {paths ["src" "resources"]}
    :as sys}]
  (misc/use-hawk sys))

(defn reitit-handler [opts]
  (misc/reitit-handler opts))

(defn use-jetty
  "Starts a Jetty web server.

  websockets: A map from paths to handlers, e.g. {\"/api/chsk\" ...}."
  [sys]
  (misc/use-jetty sys))

(defn jwt-encrypt
  [claims secret]
  (misc/jwt-encrypt claims secret))

(defn jwt-decrypt
  [token secret]
  (misc/jwt-decrypt token secret))

(defn use-chime
  [sys]
  (misc/use-chime sys))

(defn mailersend [sys opts]
  (misc/mailersend sys opts))

(defn generate-secret [length]
  (misc/generate-secret length))

(defn use-random-default-secrets [sys]
  (misc/use-random-default-secrets sys))

;;;; middleware

(def wrap-anti-forgery-websockets middle/wrap-anti-forgery-websockets)

(def wrap-render-rum middle/wrap-render-rum)

(def wrap-index-files middle/wrap-index-files)

(def wrap-resource middle/wrap-resource)

(def wrap-internal-error middle/wrap-internal-error)

(def wrap-log-requests middle/wrap-log-requests)

(def wrap-ring-defaults middle/wrap-ring-defaults)

(def wrap-env middle/wrap-env)

(def wrap-inner-defaults middle/wrap-inner-defaults)

(defn use-outer-default-middleware [sys]
  (update sys :biff/handler middle/wrap-outer-defaults sys))

;;;; xtdb

(defn start-node [opts]
  (bxt/start-node opts))

(defn use-xt [sys]
  (bxt/use-xt sys))

(defn use-tx-listener [sys]
  (bxt/use-tx-listener sys))

(defn assoc-db [sys]
  (bxt/assoc-db sys))

(defn q [& args]
  (apply bxt/q args))

(defn lazy-q [& args]
  (apply bxt/lazy-q args))

(defn lookup [db k v]
  (bxt/lookup db k v))

(defn lookup-id [db k v]
  (bxt/lookup-id db k v))

(defn submit-tx [sys tx]
  (bxt/submit-tx sys tx))

;;;; rum

(defn render [body]
  (brum/render body))

(defn unsafe
  "Returns {:dangerouslySetInnerHTML {:__html html}}, for use with Rum."
  [html]
  (brum/unsafe html))

(def emdash brum/emdash)

(def endash brum/endash)

(def nbsp brum/nbsp)

(defn g-fonts [families]
  (brum/g-fonts families))

(defn base-html [opts & body]
  (apply brum/base-html opts body))

(defn form [opts & body]
  (apply brum/form opts body))

(defn export-rum [pages dir]
  (brum/export-rum pages dir))

;;;; time

(defn now []
  (java.util.Date.))

(def rfc3339 time/rfc3339)

(def parse-date time/parse-date)

(def format-date time/format-date)

(def crop-date time/crop-date)

(def crop-day time/crop-day)

(defn seconds-between [t1 t2]
  (time/seconds-between t1 t2))

(defn seconds-in [x unit]
  (time/seconds-in x unit))

(defn elapsed? [t1 t2 x unit]
  (time/elapsed? t1 t2 x unit))

(defn between-hours? [t h1 h2]
  (time/between-hours? t h1 h2))

(defn add-seconds [date seconds]
  (time/add-seconds date seconds))
