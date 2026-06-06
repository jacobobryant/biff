(ns com.biffweb.config
  "A light Biff wrapper around Aero.

   SCHEMA

   :biff.config/profile
   keyword

     The Aero :profile value. Intended for testing/development; see use-aero-config.

   :biff.config/system-properties
   {\"property\" \"value\", ...}

     See use-aero-config."
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.biffweb.core :as biff.core]))

(biff.core/register
 {:biff.config/profile           'keyword?
  :biff.config/system-properties 'map?})

;; Algorithm adapted from dotenv-java:
;; https://github.com/cdimascio/dotenv-java/blob/master/src/main/java/io/github/cdimascio/dotenv/internal/DotenvParser.java
;; Wouldn't hurt to take a more thorough look at Ruby dotenv's algorithm:
;; https://github.com/bkeepers/dotenv/blob/master/lib/dotenv/parser.rb
(defn- parse-env-var [line]
  (let [line      (str/trim line)
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
  [{:keys [biff.aero/env]} _ value]
  (not-empty (get env (str value))))

(defmethod aero/reader 'biff/secret
  [opts _ value]
  (when-some [value (aero/reader opts 'biff/env value)]
    (biff.core/secret-delay value)))

(defn- get-env []
  (reduce into
          {}
          [(some->> (try (slurp "config.env") (catch Exception _ nil))
                    str/split-lines
                    (keep parse-env-var))
           (System/getenv)
           (keep (fn [[k v]]
                   (when (str/starts-with? k "biff.env.")
                     [(str/replace k #"^biff.env." "") v]))
                 (System/getProperties))]))

(defn- remove-nil-values [m]
  (into {}
        (remove (comp nil? val))
        m))

(defn- system-properties-compat [ctx]
  (into {}
        (keep (fn [[k v]]
                (when (and (keyword? k)
                           (= "biff.system-properties" (namespace k)))
                  [(name k) v])))
        ctx))

(defn use-aero-config
  "Parses config.edn and merges into ctx. Also sets system properties.

   Loads a config.edn file from resources and parses it with Aero. (See
   https://github.com/juxt/aero). Two additional reader tags are supported:
   #biff/env and #biff/secret. Keys with nil values (e.g. from unset env vars)
   are filtered out.

   #biff/env is like #env, but environment variables can also be specified in an
   optional config.env file (read from the filesystem, not from resources) and
   in the system properties (variable names should be prefixed with biff.env,
   e.g biff.env.BIFF_PROFILE). If values are defined in multiple places,
   precedence is as follows:

     1. System properties
     2. Actual environment variables
     3. config.env

   The :profile value for Aero is also taken from these sources, in the
   BIFF_PROFILE key (e.g. `BIFF_PROFILE=prod` -- the value is converted to a
   keyword). It can also be passed in with `ctx` via the :biff.config/profile
   key, but this is only intended as a convenience for inspecting your config
   from the REPL.

   #biff/secret is like #biff/env, but wraps values in biff.core/secret-delay so
   that they aren't visible if you serialize the system map. Secrets can be
   unwrapped with `force`:

     (let [{:keys [com.example/my-api-key]} ctx]
       (force my-api-key))

   After config is merged into ctx, any entries in
   (:biff.config/system-properties ctx) will be added to the system map:

     :biff.config/system-properties {\"user.timezone\" \"UTC\"}
     ;; Equivalent to:
     (System/setProperty \"user.timezone\" \"UTC\")

   For backwards compatibility with Biff v1:

   - secrets can be unwrapped by calling them as a zero-arg function:
   `((:com.example/api-key ctx))`

   - there is a :biff/secret function which can be used to unwrap secrets:
   `((:biff/secret ctx) :com.example/api-key)`

   - keys with a namespace of \"biff.system-properties\" are also merged into
     the system properties."
  [{:biff.config/keys [profile] :as ctx}]
  (let [env     (get-env)
        profile (some-> (or profile
                            (get env "BIFF_PROFILE")
                            ;; For backwards compatibility
                            (get env "BIFF_ENV"))
                        keyword)
        config  (aero/read-config (io/resource "config.edn")
                                  {:profile profile :biff.aero/env env})
        ctx     (merge ctx (remove-nil-values config))
        ;; For backwards compatibility
        secret  (fn [k]
                  (when-some [f (get ctx k)]
                    (f)))
        ctx     (assoc ctx :biff/secret secret)]
    (doseq [[k v] (merge (system-properties-compat ctx)
                         (get ctx :biff.config/system-properties))]
      (System/setProperty (name k) v))
    ctx))
