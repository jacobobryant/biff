(ns com.biffweb.sqlite.impl.litestream-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.biffweb.core :as biff.core]
            [com.biffweb.sqlite.impl.bin :as bin]
            [com.biffweb.sqlite.impl.defaults :as defaults]
            [com.biffweb.sqlite.impl.litestream :as litestream]))

(deftest default-version-test
  (testing "has a default version set"
    (is (string? (:biff.sqlite/litestream-version defaults/defaults)))
    (is (re-matches #"\d+\.\d+\.\d+"
                    (:biff.sqlite/litestream-version defaults/defaults)))))

(deftest credential-env-test
  (testing "uses the access key directly and invokes the secret function"
    (is (= {"LITESTREAM_ACCESS_KEY_ID"     "AKID"
            "LITESTREAM_SECRET_ACCESS_KEY" "SECRET"}
           (#'litestream/credential-env
            {:biff.sqlite/litestream-access-key-id     "AKID"
             :biff.sqlite/litestream-secret-access-key (biff.core/secret-delay "SECRET")})))))

(deftest use-litestream-skips-when-not-configured
  (testing "returns context unchanged when S3 config is absent"
    (let [ctx    {:biff.core/stop      []
                  :biff.sqlite/db-path "storage/sqlite/main.db"}
          result (litestream/use-litestream ctx)]
      (is (= ctx result)))))

(deftest write-config-test
  (testing "generates correct YAML config with env var references for secrets"
    (let [dir (str "target/test-litestream-" (System/currentTimeMillis))
          _   (.mkdirs (io/file dir))]
      (#'litestream/write-config!
       {:biff.sqlite/db-path                      "storage/sqlite/main.db"
        :biff.sqlite/litestream-dir               dir
        :biff.sqlite/litestream-bucket            "my-bucket"
        :biff.sqlite/litestream-endpoint          "https://s3.us-east-1.amazonaws.com"
        :biff.sqlite/litestream-region            "us-east-1"
        :biff.sqlite/litestream-access-key-id     "AKID"
        :biff.sqlite/litestream-secret-access-key (biff.core/secret-delay "SECRET")})
      (let [config (slurp (str dir "/litestream.yml"))]
        (is (str/includes? config "path: storage/sqlite/main.db"))
        (is (str/includes? config "bucket: my-bucket"))
        (is (str/includes? config (str "path: " dir "/main.db")))
        (is (str/includes? config "endpoint: https://s3.us-east-1.amazonaws.com"))
        (is (str/includes? config "region: us-east-1"))
        (is (str/includes? config "access-key-id: $LITESTREAM_ACCESS_KEY_ID"))
        (is (str/includes? config "secret-access-key: $LITESTREAM_SECRET_ACCESS_KEY"))
        (is (not (str/includes? config "access-key-id: AKID")))
        (is (not (str/includes? config "secret-access-key: SECRET\n"))))
      (io/delete-file (str dir "/litestream.yml") true)
      (.delete (io/file dir)))))

(deftest ensure-litestream-binary-test
  (testing "uses shared binary resolver with sqlite bin-dir"
    (let [calls (atom [])]
      (with-redefs [bin/platform-info  (constantly {:os :linux :arch :amd64})
                    bin/ensure-binary! (fn [opts]
                                         (swap! calls conj opts)
                                         "target/bin/litestream")]
        (is (= "target/bin/litestream"
               (litestream/ensure-litestream-binary! {:biff.sqlite/litestream-version "0.5.9"
                                                      :biff.sqlite/bin-dir            (:biff.sqlite/bin-dir defaults/defaults)}))))
      (is (= [{:executable-basename "litestream"
               :target-version      "0.5.9"
               :bin-dir             (:biff.sqlite/bin-dir defaults/defaults)
               :url                 "https://github.com/benbjohnson/litestream/releases/download/v0.5.9/litestream-0.5.9-linux-x86_64.tar.gz"}]
             (mapv #(select-keys % [:executable-basename
                                    :target-version
                                    :bin-dir
                                    :url])
                   @calls))))))
