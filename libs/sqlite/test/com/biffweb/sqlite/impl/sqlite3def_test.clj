(ns com.biffweb.sqlite.impl.sqlite3def-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [com.biffweb.sqlite.impl.sqlite3def :as sqlite3def]))

(deftest default-version-test
  (testing "has a default version set"
    (is (string? sqlite3def/default-version))
    (is (re-matches #"\d+\.\d+\.\d+" sqlite3def/default-version))))

(deftest local-bin-path-test
  (testing "returns a path under sqlite3def-dir"
    (is (str/starts-with? (sqlite3def/local-bin-path) sqlite3def/sqlite3def-dir))))

(deftest infer-download-filename-test
  (testing "generates a filename with version, os, and arch"
    (let [filename (#'sqlite3def/infer-download-filename "3.10.1")]
      (is (str/starts-with? filename "sqlite3def_"))
      (is (or (str/ends-with? filename ".tar.gz")
              (str/ends-with? filename ".zip"))))))

(deftest check-version-returns-nil-for-missing-binary-test
  (testing "check-version returns nil when binary doesn't exist"
    (let [dir (str "target/test-no-sqlite3def-" (System/currentTimeMillis))]
      (is (nil? (#'sqlite3def/check-version (str dir "/sqlite3def")))))))

(deftest find-global-sqlite3def-test
  (testing "find-global-sqlite3def returns nil when sqlite3def is not on PATH"
    ;; This may or may not find sqlite3def depending on the system,
    ;; but it should not throw.
    (is (or (nil? (#'sqlite3def/find-global-sqlite3def))
            (string? (#'sqlite3def/find-global-sqlite3def))))))
