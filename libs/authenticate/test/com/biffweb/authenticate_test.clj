(ns com.biffweb.authenticate-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [com.biffweb.authenticate :as auth]
            [com.biffweb.authenticate.impl.backend :as backend]
            [com.biffweb.authenticate.impl.frontend :as frontend]
            [com.biffweb.authenticate.impl.captcha :as captcha]
            [com.biffweb.authenticate.impl.system :as system]
            [com.biffweb.core :as biff]
            [com.biffweb.stuff :as stuff]
            [demo.store :as store]))

;;;; Helpers (backend) =========================================================

(deftest normalize-email-test
  (let [handler (backend/wrap-normalize-email identity)]
    (is (= "foo@bar.com"
           (get-in (handler {:biff.stuff/params
                             {:email "  FOO@BAR.COM  "}})
                   [:biff.stuff/params :email])))
    (is (nil? (get-in (handler {:biff.stuff/params {:email nil}})
                      [:biff.stuff/params :email])))
    (is (= {}
           (:biff.stuff/params (handler {:biff.stuff/params {}}))))))

(deftest email-valid-test
  (is (true? (system/email-valid? {} "test@example.com")))
  (is (true? (system/email-valid? {} "a@b.co")))
  (is (not (system/email-valid? {} nil)))
  (is (not (system/email-valid? {} "not-an-email")))
  (is (not (system/email-valid? {} "has space@test.com")))
  (is (not (system/email-valid? {} "@no-local.com")))
  (is (not (system/email-valid? {} "no-tld@test")))
  ;; not a string
  (is (not (system/email-valid? {} 123))))

(deftest new-code-test
  (let [code (system/new-code {} 6)]
    (is (= 6 (count code)))
    (is (re-matches #"\d{6}" code)))
  (let [code (system/new-code {} 4)]
    (is (= 4 (count code)))
    (is (re-matches #"\d{4}" code))))

(deftest add-query-test
  (is (= "/signin?error=foo"
         (backend/add-query "/signin" {:error "foo"})))
  (is (= "/signin?tab=code&error=foo"
         (backend/add-query "/signin?tab=code" {:error "foo"})))
  (is (= "/signin?sent-to=test%40example.com&error=invalid+code"
         (backend/add-query "/signin"
                            {:sent-to "test@example.com"
                             :error   "invalid code"}))))

(def signin-ns :biff.auth/signin)

(defn- get-signin [config ctx email]
  ((:biff.core/kv-get config) ctx signin-ns email))

(defn- put-signin! [config ctx email record]
  ((:biff.core/kv-set config) ctx signin-ns email record))

;;;; Atom store ================================================================

(deftest atom-store-test
  (let [config (store/atom-store)
        store  (::store/store config)
        ctx    {}]
    (testing "store atom is accessible"
      (is (instance? clojure.lang.Atom store)))

    (testing "initially no user exists"
      (is (nil? ((:biff.auth/get-user-id config) ctx "test@example.com"))))

    (testing "create user"
      (let [uid ((:biff.auth/create-user config)
                 ctx {:email "test@example.com" :params {:foo "bar"}})]
        (is (uuid? uid))
        (is (= uid ((:biff.auth/get-user-id config) ctx "test@example.com")))))

    (testing "signin record lifecycle"
      (let [now (java.time.Instant/now)]
        (put-signin! config ctx "test@example.com"
                     {:biff-auth-signin/code-hash       (backend/hash-secret
                                                         "123456")
                      :biff-auth-signin/created-at      now
                      :biff-auth-signin/params          {:extra "data"}
                      :biff-auth-signin/failed-attempts 0})
        (let [signin-record (get-signin config ctx "test@example.com")]
          (is (= (backend/hash-secret "123456")
                 (:biff-auth-signin/code-hash signin-record)))
          (is (= now (:biff-auth-signin/created-at signin-record)))
          (is (= 0 (:biff-auth-signin/failed-attempts signin-record)))
          (is (= {:extra "data"} (:biff-auth-signin/params signin-record))))

        (put-signin! config ctx "test@example.com"
                     (update (get-signin config ctx "test@example.com")
                             :biff-auth-signin/failed-attempts
                             (fnil inc 0)))
        (is (= 1 (:biff-auth-signin/failed-attempts
                  (get-signin config ctx "test@example.com"))))

        (put-signin! config ctx "test@example.com" nil)
        (is (nil? (get-signin config ctx "test@example.com")))))))

;;;; Send code machine =========================================================

(defn- make-send-code-ctx
  [store-config & {:keys [email send-result]
                   :or   {send-result true}}]
  {:biff.stuff/params      {:email email}
   :biff.auth/app-name     "Test App"
   :biff.auth/email-valid? system/email-valid?
   :biff.auth/signin-page  "/signin"

   :biff.fx/handlers
   {:biff.auth/get-user-id
    (:biff.auth/get-user-id store-config)

    :biff.auth/create-user
    (:biff.auth/create-user store-config)

    :biff.core/kv-get
    (:biff.core/kv-get store-config)

    :biff.core/kv-set
    (:biff.core/kv-set store-config)

    :biff.auth/captcha-verify (constantly true)
    :biff.auth/send-email     (fn [_ _] send-result)
    :biff.auth/new-code       system/new-code}})

(deftest send-code-invalid-email-test
  (let [config (store/atom-store)
        ctx    (make-send-code-ctx config :email "not-valid")
        result (backend/send-code-handler ctx)]
    (is (= 303 (:status result)))
    (is (str/includes? (get-in result [:headers "location"])
                       "error=invalid-email"))))

(deftest send-code-success-test
  (let [config      (store/atom-store)
        store       (::store/store config)
        sent-emails (atom [])
        ctx         (assoc-in (make-send-code-ctx
                               config :email "test@example.com")
                              [:biff.fx/handlers :biff.auth/send-email]
                              (fn [_ params]
                                (swap! sent-emails conj params)
                                true))
        result      (backend/send-code-handler ctx)]
    (is (= 303 (:status result)))
    (is (str/includes? (get-in result [:headers "location"])
                       "/signin?sent-to="))
    (is (str/includes? (get-in result [:headers "location"])
                       "sent-to=test%40example.com"))
    (is (= 1 (count @sent-emails)))
    (is (= "test@example.com" (:to (first @sent-emails))))
    (is (some? (:subject (first @sent-emails))))
    (is (some? (:html (first @sent-emails))))
    (is (some? (:text (first @sent-emails))))
    (let [code      (:code (first @sent-emails))
          code-hash (get-in @store [:kv signin-ns "test@example.com"
                                    :biff-auth-signin/code-hash])]
      (is (= (backend/hash-secret code) code-hash))
      (is (not= code code-hash)))))

(deftest send-code-stores-params-test
  (let [config (store/atom-store)
        store  (::store/store config)
        ctx    (assoc (make-send-code-ctx config :email "test@example.com")
                      :biff.stuff/params
                      {:email                 "test@example.com"
                       :extra                 "data"
                       :cf-turnstile-response "captcha-token"
                       :__anti-forgery-token  "keyword-token"})
        result (backend/send-code-handler ctx)]
    (is (= 303 (:status result)))
    (let [signin-record (get-in @store [:kv signin-ns "test@example.com"])]
      (is (= {:extra                 "data"
              :cf-turnstile-response "captcha-token"}
             (:biff-auth-signin/params signin-record))))))

(deftest send-code-email-send-fails-test
  (let [config (store/atom-store)
        ctx    (make-send-code-ctx config :email "test@example.com"
                                   :send-result false)
        result (backend/send-code-handler ctx)]
    (is (= 303 (:status result)))
    (is (str/includes? (get-in result [:headers "location"])
                       "error=send-failed"))))

(deftest send-code-captcha-fail-test
  (let [config (store/atom-store)
        ctx    (-> (make-send-code-ctx config :email "test@example.com")
                   (assoc-in [:biff.fx/handlers :biff.auth/captcha-verify]
                             (constantly false)))
        result (backend/send-code-handler ctx)]
    (is (= 303 (:status result)))
    (is (str/includes? (get-in result [:headers "location"]) "error=captcha"))))

;;;; Verify code machine =======================================================

(defn- make-verify-code-ctx
  [store-config & {:keys [email code]}]
  {:biff.stuff/params             {:email email :code code}
   :session                       {}
   :biff.auth/app-path            "/app"
   :biff.auth/max-failed-attempts 5
   :biff.auth/code-expiry-minutes 10
   :biff.auth/signin-page         "/signin"

   :biff.fx/handlers
   {:biff.auth/get-user-id
    (:biff.auth/get-user-id store-config)

    :biff.auth/create-user
    (:biff.auth/create-user store-config)

    :biff.core/kv-get
    (:biff.core/kv-get store-config)

    :biff.core/kv-set
    (:biff.core/kv-set store-config)

    :biff.auth/new-code system/new-code}})

(deftest verify-code-success-test
  (let [config (store/atom-store)
        now    (java.time.Instant/now)]
    (put-signin! config {} "test@example.com"
                 {:biff-auth-signin/code-hash       (backend/hash-secret
                                                     "123456")
                  :biff-auth-signin/created-at      now
                  :biff-auth-signin/failed-attempts 0
                  :biff-auth-signin/params          {}})
    (let [ctx    (make-verify-code-ctx config :email "test@example.com"
                                       :code "123456")
          result (backend/verify-code-handler ctx)]
      (is (= 303 (:status result)))
      (is (= "/app" (get-in result [:headers "location"])))
      (is (some? (get-in result [:session :uid])))
      (is (nil? (get-signin config {} "test@example.com")))
      (is (some? ((:biff.auth/get-user-id config) {} "test@example.com"))))))

(deftest verify-code-wrong-code-test
  (let [config (store/atom-store)
        now    (java.time.Instant/now)]
    (put-signin! config {} "test@example.com"
                 {:biff-auth-signin/code-hash       (backend/hash-secret
                                                     "123456")
                  :biff-auth-signin/created-at      now
                  :biff-auth-signin/failed-attempts 0
                  :biff-auth-signin/params          {}})
    (let [ctx    (make-verify-code-ctx config :email "test@example.com"
                                       :code "000000")
          result (backend/verify-code-handler ctx)]
      (is (= 303 (:status result)))
      (is (str/includes? (get-in result [:headers "location"])
                         "error=invalid-code"))
      (is (= 1 (:biff-auth-signin/failed-attempts
                (get-signin config {} "test@example.com")))))))

(deftest verify-code-expired-test
  (let [config       (store/atom-store)
        expired-time (.minus (java.time.Instant/now)
                             (java.time.Duration/ofMinutes 15))]
    (put-signin! config {} "test@example.com"
                 {:biff-auth-signin/code-hash       (backend/hash-secret
                                                     "123456")
                  :biff-auth-signin/created-at      expired-time
                  :biff-auth-signin/failed-attempts 0
                  :biff-auth-signin/params          {}})
    (let [ctx    (make-verify-code-ctx config :email "test@example.com"
                                       :code "123456")
          result (backend/verify-code-handler ctx)]
      (is (= 303 (:status result)))
      (is (str/includes? (get-in result [:headers "location"])
                         "error=invalid-code")))))

(deftest verify-code-too-many-attempts-test
  (let [config (store/atom-store)
        now    (java.time.Instant/now)]
    (put-signin! config {} "test@example.com"
                 {:biff-auth-signin/code-hash       (backend/hash-secret
                                                     "123456")
                  :biff-auth-signin/created-at      now
                  :biff-auth-signin/failed-attempts 0
                  :biff-auth-signin/params          {}})
    (dotimes [_ 5]
      (put-signin! config {} "test@example.com"
                   (update (get-signin config {} "test@example.com")
                           :biff-auth-signin/failed-attempts
                           (fnil inc 0))))
    (let [ctx    (make-verify-code-ctx config :email "test@example.com"
                                       :code "123456")
          result (backend/verify-code-handler ctx)]
      (is (= 303 (:status result)))
      (is (str/includes? (get-in result [:headers "location"])
                         "error=invalid-code")))))

(deftest verify-code-existing-user-test
  (let [config (store/atom-store)
        now    (java.time.Instant/now)
        uid    ((:biff.auth/create-user config)
                {} {:email "test@example.com"})]
    (put-signin! config {} "test@example.com"
                 {:biff-auth-signin/code-hash       (backend/hash-secret
                                                     "123456")
                  :biff-auth-signin/created-at      now
                  :biff-auth-signin/failed-attempts 0
                  :biff-auth-signin/params          {}})
    (let [ctx    (make-verify-code-ctx config :email "test@example.com"
                                       :code "123456")
          result (backend/verify-code-handler ctx)]
      (is (= 303 (:status result)))
      (is (= "/app" (get-in result [:headers "location"])))
      (is (= uid (get-in result [:session :uid]))))))

;;;; Signout ===================================================================

(deftest signout-test
  (let [result (backend/signout-handler {:session {:uid (random-uuid)}})]
    (is (= 303 (:status result)))
    (is (= "/" (get-in result [:headers "location"])))
    (is (nil? (get-in result [:session :uid])))))

;;;; Module ====================================================================

(defn- apply-middleware [handler middleware]
  (reduce (fn [handler [wrap & args]]
            (apply wrap handler args))
          handler
          (reverse middleware)))

(defn- route-nodes [module]
  (filter #(and (vector? %) (string? (first %)))
          (tree-seq coll? seq (:biff.ring/routes module))))

(def module-defaults
  {:biff.auth/skip-csrf-protection true})

(deftest module-returns-routes-test
  (let [config (store/atom-store)
        m      (auth/module (merge config auth/turnstile-config module-defaults
                                   {:biff.auth/app-name   "Test App"
                                    :biff.auth/send-email (constantly true)}))]
    (is (vector? (:biff.ring/routes m)))
    (is (= "" (first (first (:biff.ring/routes m)))))))

(deftest module-with-custom-options-test
  (let [config (store/atom-store)
        m      (auth/module (merge config auth/turnstile-config module-defaults
                                   {:biff.auth/send-email    (constantly true)
                                    :biff.auth/app-path      "/dashboard"
                                    :biff.auth/app-name      "Test App"
                                    :biff.auth/primary-color "#FF0000"}))]
    (is (some? (:biff.ring/routes m)))))

(deftest module-includes-signin-route-by-default-test
  (let [config (store/atom-store)
        m      (auth/module (merge config auth/turnstile-config module-defaults
                                   {:biff.auth/app-name   "Test App"
                                    :biff.auth/send-email (constantly true)}))]
    (is (contains? (set (map first (route-nodes m))) "/signin"))))

(deftest module-signin-routes-ignore-redirect-page-options-test
  (let [config (store/atom-store)
        m      (auth/module
                (merge config auth/turnstile-config module-defaults
                       {:biff.auth/app-name    "Test App"
                        :biff.auth/send-email  (constantly true)
                        :biff.auth/signin-page "/custom/signin"}))
        paths  (set (map first (route-nodes m)))]
    (is (contains? paths "/signin"))
    (is (not (contains? paths "/custom/signin")))))

(deftest module-omits-signin-when-disabled-test
  (let [config (store/atom-store)
        m      (auth/module (merge config auth/turnstile-config module-defaults
                                   {:biff.auth/app-name "Test App"

                                    :biff.auth/send-email
                                    (constantly true)

                                    :biff.auth/include-signin-page false}))]
    (is (not (contains? (set (map first (route-nodes m))) "/signin")))))

(deftest module-throws-on-missing-required-keys-test
  (let [[_ route-data] (first (:biff.ring/routes
                               (auth/module {:biff.auth/skip-captcha true

                                             :biff.auth/skip-csrf-protection
                                             true})))
        handler        (apply-middleware identity (:middleware route-data))]
    (is (thrown-with-msg? AssertionError
                          #"Missing required keys"
                          (handler {})))))

(deftest module-does-not-require-app-name-test
  (let [config         (store/atom-store)
        module         (auth/module
                        (merge config auth/turnstile-config module-defaults
                               {:biff.auth/send-email   (constantly true)
                                :biff.auth/skip-captcha true}))
        [_ route-data] (first (:biff.ring/routes module))
        handler        (apply-middleware identity (:middleware route-data))]
    (is (nil? (:biff.auth/app-name (handler {}))))))

(deftest module-enables-csrf-protection-by-default-test
  (let [config  (store/atom-store)
        module  (auth/module
                 (merge config
                        {:biff.auth/app-name     "Test App"
                         :biff.auth/send-email   (constantly true)
                         :biff.auth/skip-captcha true}))
        handler (->> (:biff.ring/routes module)
                     first
                     second
                     :middleware
                     (apply-middleware identity))]
    (is (= 403 (:status (handler {:request-method :post
                                  :session        {}}))))
    (is (string? (:anti-forgery-token
                  (handler {:request-method :get :session {}}))))))

(deftest module-allows-send-code-without-captcha-test
  (let [config              (store/atom-store)
        opts                {:biff.auth/app-name             "Test App"
                             :biff.auth/skip-captcha         true
                             :biff.auth/skip-csrf-protection true

                             :biff.auth/send-email
                             (constantly true)}
        module              (auth/module
                             (merge config
                                    auth/turnstile-config
                                    opts))
        [_ route-data]      (first (:biff.ring/routes module))
        [_ send-code-route] (first
                             (filter #(= "/_biff/auth/send-code" (first %))
                                     (route-nodes module)))
        handler             (apply-middleware
                             (:post send-code-route)
                             (:middleware route-data))
        result              (handler
                             (merge
                              (select-keys config
                                           [:biff.core/kv-get
                                            :biff.core/kv-set])

                              {:params {:email "test@example.com"}

                               :biff.auth/app-name "Test App"}))]
    (is (= 303 (:status result)))
    (is (str/includes? (get-in result [:headers "location"])
                       "/signin?sent-to="))))

(deftest module-requires-send-email-test
  (let [config         (store/atom-store)
        module         (auth/module
                        (merge config auth/turnstile-config module-defaults
                               {:biff.auth/app-name     "Test App"
                                :biff.auth/skip-captcha true}))
        [_ route-data] (first (:biff.ring/routes module))
        handler        (apply-middleware identity (:middleware route-data))
        ex             (try
                         (handler {})
                         nil
                         (catch AssertionError e e))]
    (is (some? ex))
    (is (str/includes? (ex-message ex) ":biff.auth/send-email"))))

(deftest module-uses-send-email-when-skip-captcha-is-true-test
  (let [config     (store/atom-store)
        captured   (atom nil)
        send-email (fn [ctx _params]
                     (reset! captured ctx)
                     true)

        [_ auth-route-data & _]
        (first (:biff.ring/routes
                (auth/module (merge config
                                    auth/turnstile-config
                                    module-defaults
                                    {:biff.auth/app-name     "Test App"
                                     :biff.auth/send-email   send-email
                                     :biff.auth/skip-captcha true}))))

        handler       (apply-middleware identity
                                        (:middleware auth-route-data))
        ctx           (handler (merge (select-keys config
                                                   [:biff.core/kv-get
                                                    :biff.core/kv-set])
                                      {:params             {}
                                       :biff.auth/app-name "Test App"
                                       :system-marker      :present}))
        fx-send-email (get-in ctx [:biff.fx/handlers
                                   :biff.auth/send-email])]
    (is (true? (fx-send-email ctx {:to "test@example.com"})))
    (is (= :present (:system-marker @captured)))))

(deftest module-send-code-route-uses-send-email-handler-test
  (let [config      (store/atom-store)
        sent-emails (atom [])
        send-email  (fn [_ctx params]
                      (swap! sent-emails conj params)
                      true)

        [_ auth-route-data & _]
        (first (:biff.ring/routes
                (auth/module (merge config
                                    auth/turnstile-config
                                    module-defaults
                                    {:biff.auth/app-name     "Test App"
                                     :biff.auth/send-email   send-email
                                     :biff.auth/skip-captcha true}))))

        handler (apply-middleware backend/send-code-handler
                                  (:middleware auth-route-data))
        result  (handler (merge (select-keys
                                 config
                                 [:biff.core/kv-get
                                  :biff.core/kv-set
                                  :biff.auth/get-user-id
                                  :biff.auth/create-user])
                                {:params {:email "test@example.com"}}))]
    (is (= 303 (:status result)))
    (is (str/includes? (get-in result [:headers "location"])
                       "/signin?sent-to="))
    (is (= 1 (count @sent-emails)))
    (is (= "test@example.com" (:to (first @sent-emails))))
    (is (= "Your sign-in code" (:subject (first @sent-emails))))
    (is (some? (:code (first @sent-emails))))
    (is (some? (:text (first @sent-emails))))
    (is (some? (:html (first @sent-emails))))))

(deftest module-throws-when-captcha-is-missing-and-skip-captcha-is-false-test
  (let [config (store/atom-store)

        [_ auth-route-data & _]
        (first (:biff.ring/routes
                (auth/module (merge config
                                    auth/turnstile-config
                                    module-defaults
                                    {:biff.auth/app-name "Test App"

                                     :biff.auth/send-email
                                     (constantly true)

                                     :biff.auth/skip-captcha false}))))

        handler (apply-middleware identity (:middleware auth-route-data))
        ex      (try
                  (handler (merge (select-keys config
                                               [:biff.core/kv-get
                                                :biff.core/kv-set])
                                  {:params             {}
                                   :biff.auth/app-name "Test App"}))
                  nil
                  (catch AssertionError e e))]
    (is (some? ex))
    (is (str/includes? (ex-message ex) "Captcha is not configured"))))

;;;; Page rendering ============================================================

(defn- render-page [handler ctx]
  ((stuff/wrap-params handler) (merge captcha/noop-config ctx)))

(deftest signin-page-renders-test
  (let [result (render-page frontend/signin-page
                            {:params                  {}
                             :anti-forgery-token      "test-token"
                             :biff.auth/app-name      "Test"
                             :biff.auth/primary-color "#4F46E5"
                             :biff.auth/accent-color  "#818CF8"
                             :biff.auth/font-family   "sans-serif"
                             :biff.auth/signin-page   "/signin"})]
    (is (= 200 (:status result)))
    (is (string? (:body result)))
    (is (str/includes? (:body result) "<!DOCTYPE html>"))
    (is (str/includes? (:body result) "Sign in / sign up"))))

(deftest signin-page-renders-without-app-name-test
  (let [result (render-page frontend/signin-page
                            {:params                  {}
                             :anti-forgery-token      "test-token"
                             :biff.auth/primary-color "#4F46E5"
                             :biff.auth/accent-color  "#818CF8"
                             :biff.auth/font-family   "sans-serif"
                             :biff.auth/signin-page   "/signin"})]
    (is (= 200 (:status result)))
    (is (str/includes? (:body result) "<title>Sign in</title>"))
    (is (not (str/includes? (:body result) "<h1")))))

(deftest signin-page-renders-captcha-when-config-is-present
  (let [result (render-page
                frontend/signin-page
                (merge auth/turnstile-config
                       {:params                       {}
                        :anti-forgery-token           "test-token"
                        :biff.auth/app-name           "Test"
                        :biff.auth/primary-color      "#4F46E5"
                        :biff.auth/accent-color       "#818CF8"
                        :biff.auth/font-family        "sans-serif"
                        :biff.auth/signin-page        "/signin"
                        :biff.auth/turnstile-secret   (biff/secret-delay "   ")
                        :biff.auth/turnstile-site-key ""}))]
    (is (= 200 (:status result)))
    (is (str/includes? (:body result) "cf-turnstile"))
    (is (str/includes? (:body result) "challenges.cloudflare.com"))))

(deftest signin-page-verify-code-view-test
  (let [result (render-page frontend/signin-page
                            {:params {:sent-to "test@example.com"}

                             :anti-forgery-token      "test-token"
                             :biff.auth/app-name      "Test"
                             :biff.auth/primary-color "#4F46E5"
                             :biff.auth/accent-color  "#818CF8"
                             :biff.auth/font-family   "sans-serif"
                             :biff.auth/signin-page   "/signin"})]
    (is (= 200 (:status result)))
    (is (str/includes? (:body result) "test@example.com"))
    (is (str/includes? (:body result) "inputmode=\"numeric\""))
    (is (not (str/includes? (:body result) "pattern=")))))

;;;; Captcha configs ===========================================================

(deftest turnstile-config-test
  (is (fn? (:biff.auth/captcha-verify auth/turnstile-config)))
  (is (fn? (:biff.auth/captcha-head auth/turnstile-config)))
  (is (fn? (:biff.auth/captcha-widget auth/turnstile-config))))

(deftest recaptcha-config-test
  (is (fn? (:biff.auth/captcha-verify auth/recaptcha-config)))
  (is (fn? (:biff.auth/captcha-head auth/recaptcha-config)))
  (is (fn? (:biff.auth/captcha-button-attrs auth/recaptcha-config))))

(deftest hcaptcha-config-test
  (is (fn? (:biff.auth/captcha-verify auth/hcaptcha-config)))
  (is (fn? (:biff.auth/captcha-head auth/hcaptcha-config)))
  (is (fn? (:biff.auth/captcha-widget auth/hcaptcha-config))))

;; Captcha head/widget are functions of ctx
(deftest captcha-head-widget-are-fns-of-ctx-test
  (testing "turnstile"
    (is (vector? ((:biff.auth/captcha-head auth/turnstile-config) {})))
    (is (vector? ((:biff.auth/captcha-widget auth/turnstile-config)
                  {:biff.auth/turnstile-site-key "test-key"}))))
  (testing "recaptcha"
    (is (vector? ((:biff.auth/captcha-head auth/recaptcha-config) {})))
    (is (map? ((:biff.auth/captcha-button-attrs auth/recaptcha-config)
               {:biff.auth/recaptcha-site-key "test-key"}))))
  (testing "hcaptcha"
    (is (vector? ((:biff.auth/captcha-head auth/hcaptcha-config) {})))
    (is (vector? ((:biff.auth/captcha-widget auth/hcaptcha-config)
                  {:biff.auth/hcaptcha-site-key "test-key"})))))

(deftest captcha-verify-start-state-forces-secrets-test
  (testing "turnstile"
    (is (= {::captcha/response [:biff.auth/http
                                {:method           :post
                                 :url              captcha/turnstile-url
                                 :form-params      {:secret   "turnstile-secret"
                                                    :response "turnstile-token"}
                                 :as               :json
                                 :coerce           :always
                                 :throw-exceptions false}]
            :biff.fx/next      :check-response}
           (captcha/turnstile-verify
            {:biff.auth/turnstile-secret (biff/secret-delay "turnstile-secret")

             :biff.stuff/params {:cf-turnstile-response "turnstile-token"}}
            :start))))
  (testing "recaptcha"
    (is (= {::captcha/response [:biff.auth/http
                                {:method           :post
                                 :url              captcha/recaptcha-url
                                 :form-params      {:secret   "recaptcha-secret"
                                                    :response "recaptcha-token"}
                                 :as               :json
                                 :coerce           :always
                                 :throw-exceptions false}]
            :biff.fx/next      :check-response}
           (captcha/recaptcha-verify
            {:biff.auth/recaptcha-secret (biff/secret-delay "recaptcha-secret")

             :biff.stuff/params {:g-recaptcha-response "recaptcha-token"}}
            :start))))
  (testing "hcaptcha"
    (is (= {::captcha/response [:biff.auth/http
                                {:method           :post
                                 :url              captcha/hcaptcha-url
                                 :form-params      {:secret   "hcaptcha-secret"
                                                    :response "hcaptcha-token"}
                                 :as               :json
                                 :coerce           :always
                                 :throw-exceptions false}]
            :biff.fx/next      :check-response}
           (captcha/hcaptcha-verify
            {:biff.auth/hcaptcha-secret (biff/secret-delay "hcaptcha-secret")
             :biff.stuff/params         {:h-captcha-response "hcaptcha-token"}}
            :start)))))

(deftest captcha-configured-requires-secret-and-site-key-presence-test
  (is (false? ((:biff.auth/captcha-configured? auth/turnstile-config)
               {:biff.auth/turnstile-secret   nil
                :biff.auth/turnstile-site-key ""})))
  (is (true? ((:biff.auth/captcha-configured? auth/recaptcha-config)
              {:biff.auth/recaptcha-secret   (biff/secret-delay "secret")
               :biff.auth/recaptcha-site-key "   "})))
  (is (false? ((:biff.auth/captcha-configured? auth/hcaptcha-config)
               {:biff.auth/hcaptcha-secret   (biff/secret-delay "secret")
                :biff.auth/hcaptcha-site-key nil})))
  (is (true? ((:biff.auth/captcha-configured? auth/turnstile-config)
              {:biff.auth/turnstile-secret   (biff/secret-delay "secret")
               :biff.auth/turnstile-site-key "site-key"}))))
