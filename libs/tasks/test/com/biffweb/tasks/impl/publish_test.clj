(ns com.biffweb.tasks.impl.publish-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [com.biffweb.tasks.impl.publish :as publish]
            [com.biffweb.tasks.impl.util :as util]
            [deps-deploy.deps-deploy :as deps-deploy]
            [deps-deploy.gpg :as gpg]))

(deftest rlwrap-guard-test
  (with-redefs [publish/inside-rlwrap? (constantly true)]
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"clojure -M:run publish"
         (publish/publish)))))

(deftest explicit-project-root-test
  (let [calls  (atom [])
        config {:biff.tasks/group-name       "com.example"
                :biff.tasks/lib-name         "example"
                :biff.tasks/lib-version      "1.0.0"
                :biff.tasks/clojars-username "user"
                :biff.tasks/clojars-secret   "secret"
                :biff.tasks/pom-data         []
                :biff.tasks/pom-scm          {}
                :biff.tasks/project-root     "libs/example"}]
    (with-redefs [util/read-config
                  (fn [_]
                    config)

                  publish/published-version? (constantly false)

                  publish/build-artifact!
                  (fn [project-root build-config]
                    (swap! calls conj [:build project-root build-config])
                    ::artifact)

                  publish/deploy!
                  (fn [deploy-config artifact]
                    (swap! calls conj [:deploy deploy-config artifact]))]
      (publish/publish))
    (is (= [[:build
             (.getCanonicalFile (io/file "libs/example"))
             (select-keys config [:biff.tasks/group-name
                                  :biff.tasks/lib-name
                                  :biff.tasks/lib-version
                                  :biff.tasks/monorepo
                                  :biff.tasks/pom-data
                                  :biff.tasks/pom-scm])]
            [:deploy
             (select-keys config [:biff.tasks/clojars-secret
                                  :biff.tasks/clojars-username
                                  :biff.tasks/gpg-sign-key-id
                                  :biff.tasks/gpg-sign-with-passphrase])
             ::artifact]]
           @calls))))

(deftest snapshot-redeploy-test
  (let [calls  (atom [])
        config {:biff.tasks/group-name       "com.example"
                :biff.tasks/lib-name         "example"
                :biff.tasks/lib-version      "2.0.0-rc24-SNAPSHOT"
                :biff.tasks/clojars-username "user"
                :biff.tasks/clojars-secret   "secret"
                :biff.tasks/pom-data         []
                :biff.tasks/pom-scm          {}}]
    (with-redefs [util/read-config (constantly config)

                  publish/published-version?
                  (fn [_]
                    (throw (ex-info "Unexpected version check" {})))

                  publish/build-artifact!
                  (fn [_ _]
                    (swap! calls conj :build)
                    ::artifact)

                  publish/deploy!
                  (fn [_ artifact]
                    (swap! calls conj [:deploy artifact]))]
      (publish/publish))
    (is (= [:build [:deploy ::artifact]] @calls))))

(deftest run-deploy-test
  (let [read-passphrase gpg/read-passphrase
        calls           (atom [])]
    (with-redefs [deps-deploy/deploy
                  (fn [options]
                    (swap! calls conj [options (gpg/read-passphrase)]))

                  gpg/read-passphrase (constantly "secret")]
      (#'publish/run-deploy! {:artifact "foo.jar"} nil)
      (#'publish/run-deploy! {:artifact "bar.jar"} "key-id"))
    (is (= [[{:artifact "foo.jar"} "secret"]
            [{:artifact "bar.jar"} nil]]
           @calls))
    (is (identical? read-passphrase gpg/read-passphrase))))

(deftest local-install-test
  (doseq [version ["1.0.0" "2.0.0-rc24-SNAPSHOT"]]
    (let [calls    (atom [])
          artifact {:jar-file "example.jar" :pom-file "pom.xml"}
          config   {:biff.tasks/group-name  "com.example"
                    :biff.tasks/lib-name    "example"
                    :biff.tasks/lib-version version
                    :biff.tasks/pom-data    []
                    :biff.tasks/pom-scm     {}

                    :biff.tasks/clojars-secret
                    (delay (throw (ex-info "Unexpected credentials" {})))

                    :biff.tasks/gpg-sign-key-id          "unused-key"
                    :biff.tasks/gpg-sign-with-passphrase true}]
      (with-redefs [util/read-config
                    (fn [{:keys [required]}]
                      (is (not-any? #{:biff.tasks/clojars-username
                                      :biff.tasks/clojars-secret} required))
                      config)

                    publish/inside-rlwrap? (constantly true)

                    publish/published-version?
                    (fn [_] (throw (ex-info "Unexpected version check" {})))

                    publish/build-artifact!
                    (fn [_ _] artifact)

                    publish/deploy!
                    (fn [& _] (throw (ex-info "Unexpected remote deploy" {})))

                    deps-deploy/deploy
                    (fn [opts] (swap! calls conj opts))]
        (publish/publish "--local"))
      (is (= [{:installer      :local
               :artifact       "example.jar"
               :pom-file       "pom.xml"
               :sign-releases? false}]
             @calls)))))

(deftest unknown-publish-flag-test
  (with-redefs [util/read-config
                (fn [& _] (throw (ex-info "Unexpected config read" {})))]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Unknown publish flag"
                          (publish/publish "--locla")))))
