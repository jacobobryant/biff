(ns com.biffweb.config-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is use-fixtures]]
            [com.biffweb.config :as biff.config]))

(def ^:private test-properties
  ["biff.env.CONFIG_TEST_OVERRIDE"
   "config.test.legacy"
   "config.test.source"])

(defn- restore-properties! [snapshot]
  (doseq [[k v] snapshot]
    (if (some? v)
      (System/setProperty k v)
      (System/clearProperty k))))

(defn- with-clean-system-properties [f]
  (let [snapshot (into {}
                       (map (fn [k]
                              [k (System/getProperty k)]))
                       test-properties)]
    (restore-properties! (zipmap test-properties (repeat nil)))
    (try
      (f)
      (finally
        (restore-properties! snapshot)))))

(use-fixtures :each with-clean-system-properties)

(defn- with-test-config-resource [f]
  (let [resource io/resource]
    (with-redefs [clojure.java.io/resource
                  (fn [path]
                    (if (= path "config.edn")
                      (resource "config-test.edn")
                      (resource path)))]
      (f))))

(deftest parse-env-var-handles-comments-quotes-and-empty-values
  (is (= ["CONFIG_TEST_SIMPLE" "plain"]
         (#'com.biffweb.config/parse-env-var "CONFIG_TEST_SIMPLE=plain")))
  (is (= ["CONFIG_TEST_QUOTED" "still # here"]
         (#'com.biffweb.config/parse-env-var
          " export CONFIG_TEST_QUOTED = \"still # here\" # trailing comment")))
  (is (= ["CONFIG_TEST_SINGLE" "quoted value"]
         (#'com.biffweb.config/parse-env-var
          "CONFIG_TEST_SINGLE='quoted value'")))
  (is (nil? (#'com.biffweb.config/parse-env-var "# comment")))
  (is (nil? (#'com.biffweb.config/parse-env-var "//// comment")))
  (is (nil? (#'com.biffweb.config/parse-env-var "CONFIG_TEST_EMPTY="))))

(deftest get-env-reads-config-env-and-overrides-with-system-properties
  (with-redefs [clojure.core/slurp
                (fn [path]
                  (is (= "config.env" path))
                  (str "CONFIG_TEST_OVERRIDE=from-file\n"
                       "CONFIG-TEST-TRIMMED =  spaced value   # comment\n"
                       "CONFIG-TEST-QUOTED='has # inside'\n"
                       "CONFIG_TEST_EMPTY=\n"))]
    (System/setProperty "biff.env.CONFIG_TEST_OVERRIDE" "from-system-property")
    (let [env (#'com.biffweb.config/get-env)]
      (is (= "from-system-property"
             (get env "CONFIG_TEST_OVERRIDE")))
      (is (= "spaced value"
             (get env "CONFIG-TEST-TRIMMED")))
      (is (= "has # inside"
             (get env "CONFIG-TEST-QUOTED")))
      (is (nil? (get env "CONFIG_TEST_EMPTY"))))))

(deftest use-aero-config-merges-values-wraps-secrets-and-applies-profile
  (with-test-config-resource
    #(with-redefs [com.biffweb.config/get-env
                   (constantly {"CONFIG_TEST_FROM_ENV" "from env"
                                "CONFIG_TEST_SECRET"   "super-secret"
                                "BIFF_PROFILE"         "prod"
                                "BIFF_ENV"             "ignored"})]
       (let [ctx (biff.config/use-aero-config {:biff.config/profile :test})]
         (is (= "from env" (:config-test/from-env ctx)))
         (is (= "profile:test" (:config-test/profile ctx)))
         (is (= "super-secret" (force (:config-test/secret ctx))))
         (is (= "super-secret" ((:biff/secret ctx) :config-test/secret)))
         (is (= "#<SecretDelay: redacted>"
                (str (:config-test/secret ctx))))
         (is (not (contains? ctx :config-test/optional)))
         (is (not (contains? ctx :config-test/nil-value)))
         (is (= "legacy only" (System/getProperty "config.test.legacy")))
         (is (= "map value" (System/getProperty "config.test.source")))))))

(deftest use-aero-config-falls-back-to-biff-env
  (with-test-config-resource
    #(with-redefs [com.biffweb.config/get-env
                   (constantly {"BIFF_ENV" "prod"})]
       (is (= "profile:prod"
              (:config-test/profile (biff.config/use-aero-config {})))))))
