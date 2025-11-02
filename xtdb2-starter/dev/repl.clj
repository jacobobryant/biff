(ns repl
  (:require
   [com.biffweb :as biff]
   [com.biffweb.experimental :as biffx]
   [com.example :as main]
   [xtdb.api :as xt])
  (:import
   [java.time Instant]))

;; REPL-driven development
;; ----------------------------------------------------------------------------------------
;; If you're new to REPL-driven development, Biff makes it easy to get started: whenever
;; you save a file, your changes will be evaluated. Biff is structured so that in most
;; cases, that's all you'll need to do for your changes to take effect. (See main/refresh
;; below for more details.)
;;
;; The `clj -M:dev dev` command also starts an nREPL server on port 7888, so if you're
;; already familiar with REPL-driven development, you can connect to that with your editor.
;;
;; If you're used to jacking in with your editor first and then starting your app via the
;; REPL, you will need to instead connect your editor to the nREPL server that `clj -M:dev
;; dev` starts. e.g. if you use emacs, instead of running `cider-jack-in`, you would run
;; `cider-connect`. See "Connecting to a Running nREPL Server:"
;; https://docs.cider.mx/cider/basics/up_and_running.html#connect-to-a-running-nrepl-server
;; ----------------------------------------------------------------------------------------

;; This function should only be used from the REPL. Regular application code
;; should receive the system map from the parent Biff component. For example,
;; the use-jetty component merges the system map into incoming Ring requests.
(defn get-context []
  (biff/merge-context @main/system))

(defn add-fixtures []
  (let [user-id (random-uuid)]
    (biffx/submit-tx (get-context)
      [[:put-docs :user {:xt/id user-id
                         :email "a@example.com"
                         :foo "Some Value"
                         :joined-at (Instant/now)}]
       [:put-docs :msg {:xt/id (random-uuid)
                        :user user-id
                        :text "hello there"
                        :sent-at (Instant/now)}]])))

(defn check-config []
  (let [prod-config (biff/use-aero-config {:biff.config/profile "prod"})
        dev-config  (biff/use-aero-config {:biff.config/profile "dev"})
        ;; Add keys for any other secrets you've added to resources/config.edn
        secret-keys [:biff.middleware/cookie-secret
                     :biff/jwt-secret
                     :mailersend/api-key
                     :recaptcha/secret-key
                     ; ...
                     ]
        get-secrets (fn [{:keys [biff/secret] :as config}]
                      (into {}
                            (map (fn [k]
                                   [k (secret k)]))
                            secret-keys))]
    {:prod-config prod-config
     :dev-config dev-config
     :prod-secrets (get-secrets prod-config)
     :dev-secrets (get-secrets dev-config)}))

(comment
  ;; Call this function if you make a change to main/initial-system,
  ;; main/components, :tasks, :queues, config.env, or deps.edn.
  (main/refresh)

  ;; Call this in dev if you'd like to add some seed data to your database. If you edit the seed
  ;; data, you can reset the database by running `rm -r storage/xtdb2` (DON'T run that in prod),
  ;; restarting your app, and calling add-fixtures again.
  (add-fixtures)

  ;; Query the database
  (let [{:keys [biff/node]} (get-context)]
    (xt/q node "select * from user"))

  ;; Update an existing user's email address
  (let [{:keys [biff/node] :as ctx} (get-context)
        [{user-id :xt/id}] (xt/q node ["select _id from user where email = ?"
                                       "hello@example.com"])]
    (biffx/submit-tx ctx
      [[:patch-docs :user {:xt/id user-id
                           :email "new.address@example.com"}]]))

  (sort (keys (get-context)))

  ;; Check the terminal for output.
  (biff/submit-job (get-context) :echo {:foo "bar"})
  (deref (biff/submit-job-for-result (get-context) :echo {:foo "bar"})))
