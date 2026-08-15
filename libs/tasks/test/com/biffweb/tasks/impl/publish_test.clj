(ns com.biffweb.tasks.impl.publish-test
  (:require [clojure.test :refer [deftest is]]
            [com.biffweb.tasks.impl.publish :as publish]
            [deps-deploy.deps-deploy :as deps-deploy]
            [deps-deploy.gpg :as gpg]))

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
