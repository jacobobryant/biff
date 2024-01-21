(ns com.biffweb.config
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;;;; Copied from com.biffweb.impl.* --------------------------------------------
(defn- ns-parts [nspace]
  (if (empty? (str nspace))
    []
    (str/split (str nspace) #"\.")))

(defn- select-ns [m nspace]
  (let [parts (ns-parts nspace)]
    (->> (keys m)
         (filterv (fn [k]
                    (= parts (take (count parts) (ns-parts (namespace k))))))
         (select-keys m))))

(defn- select-ns-as [m ns-from ns-to]
  (into {}
        (map (fn [[k v]]
               (let [new-ns-parts (->> (ns-parts (namespace k))
                                       (drop (count (ns-parts ns-from)))
                                       (concat (ns-parts ns-to)))]
                 [(if (empty? new-ns-parts)
                    (keyword (name k))
                    (keyword (str/join "." new-ns-parts) (name k)))
                  v])))
        (select-ns m ns-from)))

(defmacro catchall
  [& body]
  `(try ~@body (catch Exception ~'_ nil)))
;;;; ---------------------------------------------------------------------------

;; Algorithm adapted from dotenv-java:
;; https://github.com/cdimascio/dotenv-java/blob/master/src/main/java/io/github/cdimascio/dotenv/internal/DotenvParser.java
;; Wouldn't hurt to take a more thorough look at Ruby dotenv's algorithm:
;; https://github.com/bkeepers/dotenv/blob/master/lib/dotenv/parser.rb
(defn parse-env-var [line]
  (let [line (str/trim line)
        [_ _ k v] (re-matches #"^\s*(export\s+)?([\w.\-]+)\s*=\s*(['][^']*[']|[\"][^\"]*[\"]|[^#]*)?\s*(#.*)?$"
                              line)]
    (when-not (or (str/starts-with? line "#")
                  (str/starts-with? line "////")
                  (empty? v))
      (let [v (str/trim v)
            v (if (or (re-matches #"^\".*\"$" v)
                      (re-matches #"^'.*'$" v))
                (subs v 1 (dec (count v)))
                v)]
        [k v]))))

(defmethod aero/reader 'biff/env
  [{:keys [profile biff.aero/env] :as opts} _ value]
  (not-empty (get env (str value))))

(defmethod aero/reader 'biff/secret
  [{:keys [profile biff.aero/env] :as opts} _ value]
  (when-some [value (aero/reader opts 'biff/env value)]
    (fn [] value)))

(defn get-env []
  (reduce into
          {}
          [(some->> (catchall (slurp "config.env"))
                    str/split-lines
                    (keep parse-env-var))
           (System/getenv)
           (keep (fn [[k v]]
                   (when (str/starts-with? k "biff.env.")
                     [(str/replace k #"^biff.env." "") v]))
                 (System/getProperties))]))

(defn use-aero-config [{:biff.config/keys [skip-validation] :as ctx}]
  (let [env (get-env)
        profile (some-> (or (get env "BIFF_PROFILE")
                            ;; For backwards compatibility
                            (get env "BIFF_ENV"))
                        keyword)
        ctx (merge ctx (aero/read-config (io/resource "config.edn") {:profile profile :biff.aero/env env}))
        secret (fn [k]
                 (some-> (get ctx k) (.invoke)))
        ctx (assoc ctx :biff/secret secret)]
    (when-not (or skip-validation
                  (and (secret :biff.middleware/cookie-secret)
                       (secret :biff/jwt-secret)))
      (binding [*out* *err*]
        (println "Secrets are missing. Make sure you have a config.env file in the current "
                  "directory, or set config via environment variables.")
        (System/exit 1)))
    (doseq [[k v] (select-ns-as ctx 'biff.system-properties nil)]
      (System/setProperty (name k) v))
    ctx))
