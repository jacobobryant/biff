(ns com.biffweb.sqlite.schema-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb.sqlite.schema :as schema]))

(deftest column-helpers
  (is (= {:optional true} schema/?))
  (is (= {:type :uuid :primary-key true} schema/pkey))
  (is (= {:type :uuid :ref :publication/id :required true :index true
          :unique-with [:subscriber/email]}
         (schema/ref :publication/id :required :index
                     {:unique-with [:subscriber/email]})))
  (is (= {:type :enum :enum-values {0 :status/active} :unique true}
         (schema/enum {0 :status/active} :unique)))
  (is (= {:type :edn :extra-schema [:map [:value :int]]}
         (schema/edn [:map [:value :int]])))
  (doseq [[helper type] [[schema/text :text]
                         [schema/inst :inst]
                         [schema/int :int]
                         [schema/bool :boolean]
                         [schema/blob :blob]
                         [schema/uuid :uuid]
                         [schema/real :real]]]
    (is (= {:type type} (helper)))
    (is (= {:type type :required true :index true :default 1}
           (helper :required :index {:default 1}))))
  (is (thrown? clojure.lang.ExceptionInfo
               (schema/text :required :unique-with [:subscriber/email]))))
