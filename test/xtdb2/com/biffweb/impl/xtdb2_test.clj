(ns xtdb2.com.biffweb.impl.xtdb2-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb.impl.xtdb2 :as xt2]))

(def user
  [:map {:closed true
         :biff/table "users"}
   [:xt/id :int]
   [:user/email :string]
   [:user/favorite-color {:optional true} :keyword]])

(def user-no-table
  [:map {:closed true}
   [:xt/id :int]
   [:user/email :string]])

(deftest where-clause
  (is (= (xt2/where-clause [:foo :foo/bar :foo.bar/baz :foo.bar/baz-quux])
         "foo = ? and foo$bar = ? and foo$bar$baz = ? and foo$bar$baz_quux = ?")))

(deftest put-patch
  (is (= (xt2/put user {:xt/id 1 :user/email "hello@example.com"})
         [:put-docs "users" {:xt/id 1, :user/email "hello@example.com"}]))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Unable to infer a table name."
                        (xt2/put user-no-table {:xt/id 1 :user/email "hello@example.com"})))
  (is (= (xt2/put :any {:xt/id 1 :user/email "hello@example.com"})
         [:put-docs "any" {:xt/id 1, :user/email "hello@example.com"}]))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Record is missing an :xt/id value."
                        (xt2/put user {:user/email "hello@example.com"})))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Record doesn't match schema."
                        (xt2/put user {:xt/id 1})))
  (is (= (xt2/patch user {:xt/id 1 :user/favorite-color :blue})
         [:patch-docs "users" {:xt/id 1, :user/favorite-color :blue}])))

(deftest assert-unique
  (is (= (xt2/assert-unique user {:user/email "hello@example.com"})
         ["assert 1 >= (select count(*) from users where user$email = ?"
          "hello@example.com"])))

(deftest select-where
  (is (= (xt2/select-where user {:user/email "hello@example.com"})
         ["select * from users where user$email = ?" "hello@example.com"])))

(deftest use-xtdb2
  (is (= (xt2/use-xtdb2-config {:biff/secret {}})
         {:log [:local {:path "storage/xtdb2/log"}],
          :storage [:local {:path "storage/xtdb2/storage"}]}))
  (is (= (xt2/use-xtdb2-config {:biff/secret {}
                                :biff.xtdb2/log :kafka})
         {:log [:kafka
                {:bootstrap-servers "localhost:9092", :topic "xtdb-log", :epoch 1}],
          :storage [:local {:path "storage/xtdb2/storage"}]}))
  (is (= (xt2/use-xtdb2-config {:biff/secret {:biff.xtdb2.storage/secret-key "secret-key"}
                                :biff.xtdb2/storage :remote
                                :biff.xtdb2.storage/bucket "bucket"
                                :biff.xtdb2.storage/endpoint "endpoint"
                                :biff.xtdb2.storage/access-key "access-key"})
         {:log [:local {:path "storage/xtdb2/log"}],
          :storage [:remote
                    {:object-store
                     [:s3
                      {:bucket "bucket",
                       :endpoint "endpoint",
                       :credentials {:access-key "access-key", :secret-key "secret-key"}}],
                     :local-disk-cache "storage/xtdb2/storage-cache"}]})))
