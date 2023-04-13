(ns com.example.repl
  (:require [com.example :as main]
            [com.biffweb :as biff :refer [q]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn get-sys []
  (biff/assoc-db @main/system))

(defn add-fixtures []
  (biff/submit-tx (get-sys)
    (-> (io/resource "fixtures.edn")
        slurp
        edn/read-string)))

(comment

  ;; Call this in dev if you'd like to add some seed data to your database. If
  ;; you edit the seed data (in resources/fixtures.edn), you can reset the
  ;; database by running `rm -r storage/xtdb` (DON'T run that in prod),
  ;; restarting your app, and calling add-fixtures again.
  (add-fixtures)

  (let [{:keys [biff/db] :as sys} (get-sys)]
    (q db
       '{:find (pull user [*])
         :where [[user :user/email]]}))

  (sort (keys @main/system))

  ;; Check the terminal for output.
  (biff/submit-job (get-sys) :echo {:foo "bar"})
  (deref (biff/submit-job-for-result (get-sys) :echo {:foo "bar"})))
