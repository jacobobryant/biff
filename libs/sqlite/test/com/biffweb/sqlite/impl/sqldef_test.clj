(ns com.biffweb.sqlite.impl.sqldef-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.biffweb.sqlite.impl.bin :as bin]
            [com.biffweb.sqlite.impl.defaults :as defaults]
            [com.biffweb.sqlite.impl.sqldef :as sqldef]))

(deftest default-version-test
  (testing "has a default version set"
    (is (string? (:biff.sqlite/sqldef-version defaults/defaults)))
    (is (re-matches #"\d+\.\d+\.\d+"
                    (:biff.sqlite/sqldef-version defaults/defaults)))))

(deftest ensure-sqldef-binary-test
  (testing "uses shared binary resolver with sqlite bin-dir"
    (let [calls (atom [])]
      (with-redefs [bin/platform-info  (constantly {:os :linux :arch :amd64})
                    bin/ensure-binary! (fn [opts]
                                         (swap! calls conj opts)
                                         "target/bin/sqlite3def")]
        (is (= "target/bin/sqlite3def"
               (sqldef/ensure-sqldef-binary! {:biff.sqlite/sqldef-version "3.10.1"
                                              :biff.sqlite/bin-dir        (:biff.sqlite/bin-dir defaults/defaults)}))))
      (is (= [{:executable-basename "sqlite3def"
               :target-version      "3.10.1"
               :bin-dir             (:biff.sqlite/bin-dir defaults/defaults)
               :url                 "https://github.com/sqldef/sqldef/releases/download/v3.10.1/sqlite3def_linux_amd64.tar.gz"}]
             (mapv #(select-keys % [:executable-basename
                                    :target-version
                                    :bin-dir
                                    :url])
                   @calls))))))
