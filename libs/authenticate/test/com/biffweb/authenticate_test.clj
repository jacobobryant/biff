(ns com.biffweb.authenticate-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [com.biffweb.authenticate :as auth]
            [com.biffweb.authenticate.impl.backend :as backend]
            [com.biffweb.authenticate.impl.frontend :as frontend]
            [com.biffweb.authenticate.impl.captcha :as captcha]
            [demo.store :as store]))

;; =============================================================================
;; Helpers (backend)
;; =============================================================================

(deftest normalize-email-test
  (is (= "foo@bar.com" (backend/normalize-email "  FOO@BAR.COM  ")))
  (is (= "test@example.com" (backend/normalize-email "test@example.com")))
  (is (nil? (backend/normalize-email nil))))

(deftest email-valid-test
  (is (true? (backend/email-valid? {} "test@example.com")))
  (is (true? (backend/email-valid? {} "a@b.co")))
  (is (not (backend/email-valid? {} nil)))
  (is (not (backend/email-valid? {} "not-an-email")))
  (is (not (backend/email-valid? {} "has space@test.com")))
  (is (not (backend/email-valid? {} "@no-local.com")))
  (is (not (backend/email-valid? {} "no-tld@test")))
  ;; not a string
  (is (not (backend/email-valid? {} 123))))

(deftest new-code-test
  (let [code (backend/new-code {} 6)]
    (is (= 6 (count code)))
    (is (re-matches #"\d{6}" code)))
  (let [code (backend/new-code {} 4)]
    (is (= 4 (count code)))
    (is (re-matches #"\d{4}" code))))

(deftest new-link-token-test
  (let [token (backend/new-link-token {} 32)]
    (is (= 64 (count token)))
    (is (re-matches #"[0-9a-f]{64}" token))))

(deftest payload-encoding-test
  (let [data    {:token "abc123" :email "test@example.com" :state "xyz"}
        encoded (backend/decode-payload
                 (#'backend/encode-payload data))]
    (is (= data encoded))))

(deftest append-query-params-test
  (is (= "/signin?error=foo" (backend/append-query-params "/signin" "error=foo")))
  (is (= "/signin?tab=code&error=foo" (backend/append-query-params "/signin?tab=code" "error=foo"))))

(def signin-ns :biff.auth/signin)

(defn- get-signin [config ctx email]
  ((:biff.core/kv-get config) ctx signin-ns email))

(defn- put-signin! [config ctx email record]
  ((:biff.core/kv-set config) ctx signin-ns email record))

;; =============================================================================
;; Atom store
;; =============================================================================

(deftest atom-store-test
  (let [config (store/atom-store)
        store  (::store/store config)
        ctx    {}]
    (testing "store atom is accessible"
      (is (instance? clojure.lang.Atom store)))

    (testing "initially no user exists"
      (is (nil? ((:biff.auth/get-user-id config) ctx "test@example.com"))))

    (testing "create user"
      (let [uid ((:biff.auth/create-user! config) ctx {:email "test@example.com" :params {:foo "bar"}})]
        (is (uuid? uid))
        (is (= uid ((:biff.auth/get-user-id config) ctx "test@example.com")))))

    (testing "signin record lifecycle"
      (let [now (java.time.Instant/now)]
        (put-signin! config ctx "test@example.com"
                     {:biff-auth-signin/code            "123456"
                      :biff-auth-signin/created-at      now
                      :biff-auth-signin/params          {:extra "data"}
                      :biff-auth-signin/failed-attempts 0})
        (let [signin-record (get-signin config ctx "test@example.com")]
          (is (= "123456" (:biff-auth-signin/code signin-record)))
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

;; =============================================================================
;; Send code machine
;; =============================================================================

(defn- make-send-code-ctx
  [store-config & {:keys [email send-result]
                   :or   {send-result true}}]
  {:params                     {:email email}
   :biff.auth/app-name         "Test App"
   :biff.auth/email-validator  backend/email-valid?
   :biff.auth/code-signin-path "/signin"
   :biff.fx/handlers
   (merge (select-keys store-config [:biff.auth/get-user-id :biff.auth/create-user!
                                     :biff.core/kv-get :biff.core/kv-set])
          {:biff.auth/verify-captcha (constantly {:success true})
           :biff.auth/send-email     (fn [_ _] send-result)
           :biff.auth/new-code       backend/new-code
           :biff.auth/new-link-token backend/new-link-token})})

(deftest send-code-invalid-email-test
  (let [config (store/atom-store)
        ctx    (make-send-code-ctx config :email "not-valid")
        result (backend/send-code-handler ctx)]
    (is (= 303 (:status result)))
    (is (str/includes? (get-in result [:headers "location"]) "error=invalid-email"))))

(deftest send-code-success-test
  (let [config      (store/atom-store)
        store       (::store/store config)
        sent-emails (atom [])
        ctx         (assoc-in (make-send-code-ctx config :email "test@example.com")
                              [:biff.fx/handlers :biff.auth/send-email]
                              (fn [_ params]
                                (swap! sent-emails conj params)
                                true))
        result      (backend/send-code-handler ctx)]
    (is (= 303 (:status result)))
    (is (str/includes? (get-in result [:headers "location"]) "verify=code"))
    (is (str/includes? (get-in result [:headers "location"])
                       "email=test%40example.com"))
    (is (= 1 (count @sent-emails)))
    (is (= :signin-code (:template (first @sent-emails))))
    (is (= "test@example.com" (:to (first @sent-emails))))
    (is (some? (:subject (first @sent-emails))))
    (is (some? (:html (first @sent-emails))))
    (is (some? (:text (first @sent-emails))))
    (is (some? (get-in @store [:kv signin-ns "test@example.com" :biff-auth-signin/code])))))

(deftest send-code-stores-params-test
  (let [config (store/atom-store)
        store  (::store/store config)
        ctx    (assoc (make-send-code-ctx config :email "test@example.com")
                      :params {:email "test@example.com" :extra "data"})
        result (backend/send-code-handler ctx)]
    (is (= 303 (:status result)))
    (let [signin-record (get-in @store [:kv signin-ns "test@example.com"])]
      (is (= {:extra "data"} (:biff-auth-signin/params signin-record))))))

(deftest send-code-email-send-fails-test
  (let [config (store/atom-store)
        ctx    (make-send-code-ctx config :email "test@example.com" :send-result false)
        result (backend/send-code-handler ctx)]
    (is (= 303 (:status result)))
    (is (str/includes? (get-in result [:headers "location"]) "error=send-failed"))))

(deftest send-code-captcha-fail-test
  (let [config (store/atom-store)
        ctx    (-> (make-send-code-ctx config :email "test@example.com")
                   (assoc-in [:biff.fx/handlers :biff.auth/verify-captcha]
                             (constantly {:success false})))
        result (backend/send-code-handler ctx)]
    (is (= 303 (:status result)))
    (is (str/includes? (get-in result [:headers "location"]) "error=captcha"))))

;; =============================================================================
;; Send link machine
;; =============================================================================

(defn- make-send-link-ctx
  [store-config & {:keys [email send-result]
                   :or   {send-result true}}]
  {:params                     {:email email}
   :session                    {}
   :biff.auth/app-name         "Test App"
   :biff.auth/email-validator  backend/email-valid?
   :biff.auth/base-url         "https://example.com"
   :biff.auth/link-signin-path "/signin"
   :biff.fx/handlers
   (merge (select-keys store-config [:biff.auth/get-user-id :biff.auth/create-user!
                                     :biff.core/kv-get :biff.core/kv-set])
          {:biff.auth/verify-captcha (constantly {:success true})
           :biff.auth/send-email     (fn [_ _] send-result)
           :biff.auth/new-code       backend/new-code
           :biff.auth/new-link-token backend/new-link-token})})

(deftest send-link-invalid-email-test
  (let [config (store/atom-store)
        ctx    (make-send-link-ctx config :email "not-valid")
        result (backend/send-link-handler ctx)]
    (is (= 303 (:status result)))
    (is (str/includes? (get-in result [:headers "location"]) "error=invalid-email"))))

(deftest send-link-success-test
  (let [config      (store/atom-store)
        store       (::store/store config)
        sent-emails (atom [])
        ctx         (assoc-in (make-send-link-ctx config :email "test@example.com")
                              [:biff.fx/handlers :biff.auth/send-email]
                              (fn [_ params]
                                (swap! sent-emails conj params)
                                true))
        result      (backend/send-link-handler ctx)]
    (is (= 303 (:status result)))
    (is (str/includes? (get-in result [:headers "location"]) "verify=link"))
    (is (str/includes? (get-in result [:headers "location"])
                       "email=test%40example.com"))
    (is (= 1 (count @sent-emails)))
    (is (= :signin-link (:template (first @sent-emails))))
    (is (= "test@example.com" (:to (first @sent-emails))))
    (is (str/includes? (:url (first @sent-emails)) "/_biff/auth/verify-link/"))
    (is (some? (:subject (first @sent-emails))))
    (is (some? (get-in @store [:kv signin-ns "test@example.com" :biff-auth-signin/code])))
     ;; Should set state token in session
    (is (some? (get-in result [:session :biff.auth/state])))))

(deftest send-link-captcha-fail-test
  (let [config (store/atom-store)
        ctx    (-> (make-send-link-ctx config :email "test@example.com")
                   (assoc-in [:biff.fx/handlers :biff.auth/verify-captcha]
                             (constantly {:success false})))
        result (backend/send-link-handler ctx)]
    (is (= 303 (:status result)))
    (is (str/includes? (get-in result [:headers "location"]) "error=captcha"))))

;; =============================================================================
;; Verify code machine
;; =============================================================================

(defn- make-verify-code-ctx
  [store-config & {:keys [email code]}]
  {:params                        {:email email :code code}
   :session                       {}
   :biff.auth/app-path            "/app"
   :biff.auth/max-failed-attempts 5
   :biff.auth/code-expiry-minutes 10
   :biff.auth/code-signin-path    "/signin"
   :biff.fx/handlers
   (merge (select-keys store-config [:biff.auth/get-user-id :biff.auth/create-user!
                                     :biff.core/kv-get :biff.core/kv-set])
          {:biff.auth/new-code       backend/new-code
           :biff.auth/new-link-token backend/new-link-token})})

(deftest verify-code-success-test
  (let [config (store/atom-store)
        now    (java.time.Instant/now)]
    (put-signin! config {} "test@example.com"
                 {:biff-auth-signin/code            "123456"
                  :biff-auth-signin/created-at      now
                  :biff-auth-signin/failed-attempts 0})
    (let [ctx    (make-verify-code-ctx config :email "test@example.com" :code "123456")
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
                 {:biff-auth-signin/code            "123456"
                  :biff-auth-signin/created-at      now
                  :biff-auth-signin/failed-attempts 0})
    (let [ctx    (make-verify-code-ctx config :email "test@example.com" :code "000000")
          result (backend/verify-code-handler ctx)]
      (is (= 303 (:status result)))
      (is (str/includes? (get-in result [:headers "location"]) "error=invalid-code"))
      (is (= 1 (:biff-auth-signin/failed-attempts
                (get-signin config {} "test@example.com")))))))

(deftest verify-code-expired-test
  (let [config       (store/atom-store)
        expired-time (.minus (java.time.Instant/now) (java.time.Duration/ofMinutes 15))]
    (put-signin! config {} "test@example.com"
                 {:biff-auth-signin/code            "123456"
                  :biff-auth-signin/created-at      expired-time
                  :biff-auth-signin/failed-attempts 0})
    (let [ctx    (make-verify-code-ctx config :email "test@example.com" :code "123456")
          result (backend/verify-code-handler ctx)]
      (is (= 303 (:status result)))
      (is (str/includes? (get-in result [:headers "location"]) "error=invalid-code")))))

(deftest verify-code-too-many-attempts-test
  (let [config (store/atom-store)
        now    (java.time.Instant/now)]
    (put-signin! config {} "test@example.com"
                 {:biff-auth-signin/code            "123456"
                  :biff-auth-signin/created-at      now
                  :biff-auth-signin/failed-attempts 0})
    (dotimes [_ 5]
      (put-signin! config {} "test@example.com"
                   (update (get-signin config {} "test@example.com")
                           :biff-auth-signin/failed-attempts
                           (fnil inc 0))))
    (let [ctx    (make-verify-code-ctx config :email "test@example.com" :code "123456")
          result (backend/verify-code-handler ctx)]
      (is (= 303 (:status result)))
      (is (str/includes? (get-in result [:headers "location"]) "error=invalid-code")))))

(deftest verify-code-existing-user-test
  (let [config (store/atom-store)
        now    (java.time.Instant/now)
        uid    ((:biff.auth/create-user! config) {} {:email "test@example.com"})]
    (put-signin! config {} "test@example.com"
                 {:biff-auth-signin/code            "123456"
                  :biff-auth-signin/created-at      now
                  :biff-auth-signin/failed-attempts 0})
    (let [ctx    (make-verify-code-ctx config :email "test@example.com" :code "123456")
          result (backend/verify-code-handler ctx)]
      (is (= 303 (:status result)))
      (is (= "/app" (get-in result [:headers "location"])))
      (is (= uid (get-in result [:session :uid]))))))

;; =============================================================================
;; Verify link machine
;; =============================================================================

(defn- make-verify-link-ctx
  [store-config & {:keys [email token state-token session-state]}]
  (let [payload (#'backend/encode-payload {:token token :email email :state state-token})]
    {:path-params                   {:payload payload}
     :params                        {}
     :session                       (cond-> {}
                                      session-state (assoc :biff.auth/state session-state))
     :biff.auth/app-path            "/app"
     :biff.auth/link-expiry-minutes 60
     :biff.auth/link-signin-path    "/signin"
     :biff.fx/handlers
     (merge (select-keys store-config [:biff.auth/get-user-id :biff.auth/create-user!
                                       :biff.core/kv-get :biff.core/kv-set])
            {:biff.auth/new-code       backend/new-code
             :biff.auth/new-link-token backend/new-link-token})}))

(deftest verify-link-success-test
  (let [config      (store/atom-store)
        now         (java.time.Instant/now)
        token       (backend/new-link-token {} 32)
        state-token "test-state-123"]
    (put-signin! config {} "test@example.com"
                 {:biff-auth-signin/code            token
                  :biff-auth-signin/created-at      now
                  :biff-auth-signin/failed-attempts 0})
    (let [ctx    (make-verify-link-ctx config
                                       :email "test@example.com"
                                       :token token
                                       :state-token state-token
                                       :session-state state-token)
          result (backend/verify-link-handler ctx)]
      (is (= 303 (:status result)))
      (is (= "/app" (get-in result [:headers "location"])))
      (is (some? (get-in result [:session :uid])))
      ;; State token should be removed from session
      (is (nil? (get-in result [:session :biff.auth/state])))
      (is (nil? (get-signin config {} "test@example.com"))))))

(deftest verify-link-wrong-token-test
  (let [config      (store/atom-store)
        now         (java.time.Instant/now)
        token       (backend/new-link-token {} 32)
        state-token "test-state-123"]
    (put-signin! config {} "test@example.com"
                 {:biff-auth-signin/code            token
                  :biff-auth-signin/created-at      now
                  :biff-auth-signin/failed-attempts 0})
    (let [ctx    (make-verify-link-ctx config
                                       :email "test@example.com"
                                       :token "wrong-token"
                                       :state-token state-token
                                       :session-state state-token)
          result (backend/verify-link-handler ctx)]
      (is (= 303 (:status result)))
      (is (str/includes? (get-in result [:headers "location"]) "error=invalid-link")))))

(deftest verify-link-expired-test
  (let [config       (store/atom-store)
        expired-time (.minus (java.time.Instant/now) (java.time.Duration/ofMinutes 120))
        token        (backend/new-link-token {} 32)
        state-token  "test-state-123"]
    (put-signin! config {} "test@example.com"
                 {:biff-auth-signin/code            token
                  :biff-auth-signin/created-at      expired-time
                  :biff-auth-signin/failed-attempts 0})
    (let [ctx    (make-verify-link-ctx config
                                       :email "test@example.com"
                                       :token token
                                       :state-token state-token
                                       :session-state state-token)
          result (backend/verify-link-handler ctx)]
      (is (= 303 (:status result)))
      (is (str/includes? (get-in result [:headers "location"]) "error=invalid-link")))))

(deftest verify-link-session-fixation-redirect-test
  (let [config (store/atom-store)
        now    (java.time.Instant/now)
        token  (backend/new-link-token {} 32)]
    (put-signin! config {} "test@example.com"
                 {:biff-auth-signin/code            token
                  :biff-auth-signin/created-at      now
                  :biff-auth-signin/failed-attempts 0})
    ;; State token mismatch → redirect to link-confirm
    (let [ctx    (make-verify-link-ctx config
                                       :email "test@example.com"
                                       :token token
                                       :state-token "attacker-state"
                                       :session-state "victim-state")
          result (backend/verify-link-handler ctx)]
      (is (= 303 (:status result)))
      (is (str/includes? (get-in result [:headers "location"]) "verify=link-confirm")))))

;; =============================================================================
;; Verify link confirm (session fixation protection)
;; =============================================================================

(defn- make-verify-link-confirm-ctx
  [store-config & {:keys [email token]}]
  {:params                        {:email email :token token}
   :session                       {}
   :biff.auth/app-path            "/app"
   :biff.auth/link-expiry-minutes 60
   :biff.auth/link-signin-path    "/signin"
   :biff.fx/handlers
   (merge (select-keys store-config [:biff.auth/get-user-id :biff.auth/create-user!
                                     :biff.core/kv-get :biff.core/kv-set])
          {:biff.auth/new-code       backend/new-code
           :biff.auth/new-link-token backend/new-link-token})})

(deftest verify-link-confirm-success-test
  (let [config (store/atom-store)
        now    (java.time.Instant/now)
        token  (backend/new-link-token {} 32)]
    (put-signin! config {} "test@example.com"
                 {:biff-auth-signin/code            token
                  :biff-auth-signin/created-at      now
                  :biff-auth-signin/failed-attempts 0})
    (let [ctx    (make-verify-link-confirm-ctx config
                                               :email "test@example.com"
                                               :token token)
          result (backend/verify-link-confirm-handler ctx)]
      (is (= 303 (:status result)))
      (is (= "/app" (get-in result [:headers "location"])))
      (is (some? (get-in result [:session :uid]))))))

(deftest verify-link-confirm-wrong-email-test
  (let [config (store/atom-store)
        now    (java.time.Instant/now)
        token  (backend/new-link-token {} 32)]
    (put-signin! config {} "test@example.com"
                 {:biff-auth-signin/code            token
                  :biff-auth-signin/created-at      now
                  :biff-auth-signin/failed-attempts 0})
    ;; User enters wrong email
    (let [ctx    (make-verify-link-confirm-ctx config
                                               :email "wrong@example.com"
                                               :token token)
          result (backend/verify-link-confirm-handler ctx)]
      (is (= 303 (:status result)))
      (is (str/includes? (get-in result [:headers "location"]) "error=invalid-link")))))

(deftest verify-link-confirm-nil-email-test
  (let [config (store/atom-store)
        token  (backend/new-link-token {} 32)
        ctx    (make-verify-link-confirm-ctx config :email nil :token token)
        result (backend/verify-link-confirm-handler ctx)]
    ;; Non-string email should redirect with error
    (is (= 303 (:status result)))
    (is (str/includes? (get-in result [:headers "location"]) "error=invalid-link"))))

;; =============================================================================
;; Signout
;; =============================================================================

(deftest signout-test
  (let [result (backend/signout-handler {:session {:uid (random-uuid)}})]
    (is (= 303 (:status result)))
    (is (= "/" (get-in result [:headers "location"])))
    (is (nil? (get-in result [:session :uid])))))

;; =============================================================================
;; Module
;; =============================================================================

(deftest module-returns-routes-test
  (let [config (store/atom-store)
        m      (auth/module (merge config
                                   {:biff.auth/app-name   "Test App"
                                    :biff.auth/send-email (constantly true)}))]
    (is (vector? (:biff.ring/routes m)))
    (is (= "/_biff/auth" (first (first (:biff.ring/routes m)))))))

(deftest module-with-custom-options-test
  (let [config (store/atom-store)
        m      (auth/module (merge config
                                   {:biff.auth/send-email    (constantly true)
                                    :biff.auth/app-path      "/dashboard"
                                    :biff.auth/app-name      "Test App"
                                    :biff.auth/primary-color "#FF0000"}))]
    (is (some? (:biff.ring/routes m)))))

(deftest module-includes-signin-route-by-default-test
  (let [config (store/atom-store)
        m      (auth/module (merge config
                                   {:biff.auth/app-name   "Test App"
                                    :biff.auth/send-email (constantly true)}))]
    (is (some #(= "/signin" (first %)) (:biff.ring/routes m)))))

(deftest module-omits-signin-when-disabled-test
  (let [config (store/atom-store)
        m      (auth/module (merge config
                                   {:biff.auth/app-name            "Test App"
                                    :biff.auth/send-email          (constantly true)
                                    :biff.auth/include-signin-page false}))]
    (is (not (some #(= "/signin" (first %)) (:biff.ring/routes m))))))

(deftest module-throws-on-missing-required-keys-test
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Missing required options"
                        (auth/module {}))))

(deftest module-requires-app-name-at-module-creation-test
  (let [config (store/atom-store)
        ex     (try
                 (auth/module (merge config {:biff.auth/send-email (constantly true)}))
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
    (is (some? ex))
    (is (= #{:biff.auth/app-name} (:missing (ex-data ex))))))

(deftest module-allows-send-code-when-skip-captcha-is-true-and-config-is-missing-test
  (let [config                           (store/atom-store)
        auth-routes                      (first (:biff.ring/routes
                                                 (auth/module (merge config
                                                                     auth/turnstile-config
                                                                     {:biff.auth/app-name           "Test App"
                                                                      :biff.auth/skip-captcha       true
                                                                      :biff.auth/turnstile-secret   nil
                                                                      :biff.auth/turnstile-site-key nil
                                                                      :biff.auth/send-email         (constantly true)}))))
        [_ auth-route-data & sub-routes] auth-routes
        [_ send-code-route]              (first (filter #(= "/send-code" (first %)) sub-routes))
        handler                          (reduce (fn [h [middleware arg]]
                                                   (middleware h arg))
                                                 (:post send-code-route)
                                                 (:middleware auth-route-data))
        result                           (handler (merge (select-keys config [:biff.core/kv-get :biff.core/kv-set])
                                                         {:params             {:email "test@example.com"}
                                                          :biff.auth/app-name "Test App"}))]
    (is (= 303 (:status result)))
    (is (str/includes? (get-in result [:headers "location"]) "verify=code"))))

(deftest module-requires-send-email-test
  (let [config (store/atom-store)
        ex     (try
                 (auth/module (merge config {:biff.auth/app-name "Test App"}))
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
    (is (some? ex))
    (is (= #{:biff.auth/send-email} (:missing (ex-data ex))))))

(deftest module-uses-send-email-when-skip-captcha-is-true-test
  (let [config        (store/atom-store)
        captured      (atom nil)
        send-email    (fn [ctx _params]
                        (reset! captured ctx)
                        true)
        [_ auth-route-data & _]
        (first (:biff.ring/routes
                (auth/module (merge config
                                    auth/turnstile-config
                                    {:biff.auth/app-name           "Test App"
                                     :biff.auth/send-email         send-email
                                     :biff.auth/skip-captcha       true
                                     :biff.auth/turnstile-secret   nil
                                     :biff.auth/turnstile-site-key nil}))))
        handler       (reduce (fn [h [middleware arg]]
                                (middleware h arg))
                              identity
                              (:middleware auth-route-data))
        ctx           (handler (merge (select-keys config [:biff.core/kv-get :biff.core/kv-set])
                                      {:params             {}
                                       :biff.auth/app-name "Test App"
                                       :system-marker      :present}))
        fx-send-email (get-in ctx [:biff.fx/handlers :biff.auth/send-email])]
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
                                    {:biff.auth/app-name           "Test App"
                                     :biff.auth/send-email         send-email
                                     :biff.auth/skip-captcha       true
                                     :biff.auth/turnstile-secret   nil
                                     :biff.auth/turnstile-site-key nil}))))
        handler     (reduce (fn [h [middleware arg]]
                              (middleware h arg))
                            backend/send-code-handler
                            (:middleware auth-route-data))
        result      (handler (merge (select-keys config [:biff.core/kv-get
                                                         :biff.core/kv-set
                                                         :biff.auth/get-user-id
                                                         :biff.auth/create-user!])
                                    {:params {:email "test@example.com"}}))]
    (is (= 303 (:status result)))
    (is (str/includes? (get-in result [:headers "location"]) "verify=code"))
    (is (= 1 (count @sent-emails)))
    (is (= :signin-code (:template (first @sent-emails))))
    (is (= "test@example.com" (:to (first @sent-emails))))
    (is (str/includes? (:subject (first @sent-emails)) "Test App"))
    (is (some? (:code (first @sent-emails))))
    (is (some? (:text (first @sent-emails))))
    (is (some? (:html (first @sent-emails))))))

(deftest module-throws-when-captcha-is-missing-and-skip-captcha-is-false-test
  (let [config  (store/atom-store)
        [_ auth-route-data & _]
        (first (:biff.ring/routes
                (auth/module (merge config
                                    auth/turnstile-config
                                    {:biff.auth/app-name           "Test App"
                                     :biff.auth/send-email         (constantly true)
                                     :biff.auth/skip-captcha       false
                                     :biff.auth/turnstile-secret   nil
                                     :biff.auth/turnstile-site-key nil}))))
        handler (reduce (fn [h [middleware arg]]
                          (middleware h arg))
                        identity
                        (:middleware auth-route-data))
        ex      (try
                  (handler (merge (select-keys config [:biff.core/kv-get :biff.core/kv-set])
                                  {:params             {}
                                   :biff.auth/app-name "Test App"}))
                  nil
                  (catch clojure.lang.ExceptionInfo e e))]
    (is (some? ex))
    (is (= "Captcha is not configured and :biff.auth/skip-captcha is false."
           (ex-message ex)))))

(deftest module-includes-verify-link-confirm-route-test
  (let [config      (store/atom-store)
        m           (auth/module (merge config
                                        {:biff.auth/app-name   "Test App"
                                         :biff.auth/send-email (constantly true)}))
        auth-routes (first (:biff.ring/routes m))
        sub-routes  (drop 2 auth-routes)
        paths       (map first sub-routes)]
    (is (some #(= "/verify-link-confirm" %) paths))))

;; =============================================================================
;; Page rendering
;; =============================================================================

(deftest signin-page-renders-test
  (let [result (frontend/signin-page {:params                     {}
                                      :anti-forgery-token         "test-token"
                                      :biff.auth/app-name         "Test"
                                      :biff.auth/primary-color    "#4F46E5"
                                      :biff.auth/accent-color     "#818CF8"
                                      :biff.auth/font-family      "sans-serif"
                                      :biff.auth/code-signin-path "/signin"
                                      :biff.auth/link-signin-path "/signin"})]
    (is (= 200 (:status result)))
    (is (string? (:body result)))
    (is (str/includes? (:body result) "<!DOCTYPE html>"))))

(deftest signin-page-renders-captcha-when-config-is-present
  (let [result (frontend/signin-page
                (merge auth/turnstile-config
                       {:params                       {}
                        :anti-forgery-token           "test-token"
                        :biff.auth/app-name           "Test"
                        :biff.auth/primary-color      "#4F46E5"
                        :biff.auth/accent-color       "#818CF8"
                        :biff.auth/font-family        "sans-serif"
                        :biff.auth/code-signin-path   "/signin"
                        :biff.auth/link-signin-path   "/signin"
                        :biff.auth/turnstile-secret   (constantly "   ")
                        :biff.auth/turnstile-site-key ""}))]
    (is (= 200 (:status result)))
    (is (str/includes? (:body result) "cf-turnstile"))
    (is (str/includes? (:body result) "challenges.cloudflare.com"))))

(deftest signin-page-verify-code-view-test
  (let [result (frontend/signin-page {:params                     {:verify "code" :email "test@example.com"}
                                      :anti-forgery-token         "test-token"
                                      :biff.auth/app-name         "Test"
                                      :biff.auth/primary-color    "#4F46E5"
                                      :biff.auth/accent-color     "#818CF8"
                                      :biff.auth/font-family      "sans-serif"
                                      :biff.auth/code-signin-path "/signin"
                                      :biff.auth/link-signin-path "/signin"})]
    (is (= 200 (:status result)))
    (is (str/includes? (:body result) "test@example.com"))))

(deftest signin-page-link-sent-view-test
  (let [result (frontend/signin-page {:params                     {:verify "link" :email "test@example.com"}
                                      :biff.auth/app-name         "Test"
                                      :biff.auth/primary-color    "#4F46E5"
                                      :biff.auth/accent-color     "#818CF8"
                                      :biff.auth/font-family      "sans-serif"
                                      :biff.auth/code-signin-path "/signin"
                                      :biff.auth/link-signin-path "/signin"})]
    (is (= 200 (:status result)))
    (is (str/includes? (:body result) "test@example.com"))))

(deftest signin-page-signup-tab-test
  (let [result (frontend/signin-page {:params                     {:tab "signup"}
                                      :anti-forgery-token         "test-token"
                                      :biff.auth/app-name         "Test"
                                      :biff.auth/primary-color    "#4F46E5"
                                      :biff.auth/accent-color     "#818CF8"
                                      :biff.auth/font-family      "sans-serif"
                                      :biff.auth/code-signin-path "/signin"
                                      :biff.auth/link-signin-path "/signin"})]
    (is (= 200 (:status result)))
    (is (str/includes? (:body result) "send-link"))))

(deftest signin-page-link-confirm-view-test
  (let [result (frontend/signin-page {:params                     {:verify "link-confirm" :token "abc123"}
                                      :anti-forgery-token         "test-token"
                                      :biff.auth/app-name         "Test"
                                      :biff.auth/primary-color    "#4F46E5"
                                      :biff.auth/accent-color     "#818CF8"
                                      :biff.auth/font-family      "sans-serif"
                                      :biff.auth/code-signin-path "/signin"
                                      :biff.auth/link-signin-path "/signin"})]
    (is (= 200 (:status result)))
    (is (str/includes? (:body result) "Confirm your email"))))

;; =============================================================================
;; Captcha configs
;; =============================================================================

(deftest turnstile-config-test
  (is (fn? (:biff.auth/verify-captcha auth/turnstile-config)))
  (is (fn? (:biff.auth/captcha-head auth/turnstile-config)))
  (is (fn? (:biff.auth/captcha-widget auth/turnstile-config)))
  (is (= :cf-turnstile-response (:biff.auth/captcha-param auth/turnstile-config))))

(deftest recaptcha-config-test
  (is (fn? (:biff.auth/verify-captcha auth/recaptcha-config)))
  (is (fn? (:biff.auth/captcha-head auth/recaptcha-config)))
  (is (fn? (:biff.auth/captcha-button-attrs auth/recaptcha-config)))
  (is (= :g-recaptcha-response (:biff.auth/captcha-param auth/recaptcha-config))))

(deftest hcaptcha-config-test
  (is (fn? (:biff.auth/verify-captcha auth/hcaptcha-config)))
  (is (fn? (:biff.auth/captcha-head auth/hcaptcha-config)))
  (is (fn? (:biff.auth/captcha-widget auth/hcaptcha-config)))
  (is (= :h-captcha-response (:biff.auth/captcha-param auth/hcaptcha-config))))

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

(deftest captcha-verify-start-state-uses-secret-functions-test
  (testing "turnstile"
    (is (= {:response     [:biff.fx/http
                           {:method           :post
                            :url              captcha/turnstile-url
                            :form-params      {:secret   "turnstile-secret"
                                               :response "turnstile-token"}
                            :as               :json
                            :coerce           :always
                            :throw-exceptions false}]
            :biff.fx/next :check-response}
           (captcha/turnstile-verify
            {:biff.auth/turnstile-secret (constantly "turnstile-secret")
             :params                     {:cf-turnstile-response "turnstile-token"}}
            :start))))
  (testing "recaptcha"
    (is (= {:response     [:biff.fx/http
                           {:method           :post
                            :url              captcha/recaptcha-url
                            :form-params      {:secret   "recaptcha-secret"
                                               :response "recaptcha-token"}
                            :as               :json
                            :coerce           :always
                            :throw-exceptions false}]
            :biff.fx/next :check-response}
           (captcha/recaptcha-verify
            {:biff.auth/recaptcha-secret (constantly "recaptcha-secret")
             :params                     {:g-recaptcha-response "recaptcha-token"}}
            :start))))
  (testing "hcaptcha"
    (is (= {:response     [:biff.fx/http
                           {:method           :post
                            :url              captcha/hcaptcha-url
                            :form-params      {:secret   "hcaptcha-secret"
                                               :response "hcaptcha-token"}
                            :as               :json
                            :coerce           :always
                            :throw-exceptions false}]
            :biff.fx/next :check-response}
           (captcha/hcaptcha-verify
            {:biff.auth/hcaptcha-secret (constantly "hcaptcha-secret")
             :params                    {:h-captcha-response "hcaptcha-token"}}
            :start)))))

(deftest captcha-configured-requires-secret-and-site-key-presence-test
  (is (false? ((:biff.auth/captcha-configured? auth/turnstile-config)
               {:biff.auth/turnstile-secret   nil
                :biff.auth/turnstile-site-key ""})))
  (is (true? ((:biff.auth/captcha-configured? auth/recaptcha-config)
              {:biff.auth/recaptcha-secret   (constantly "secret")
               :biff.auth/recaptcha-site-key "   "})))
  (is (false? ((:biff.auth/captcha-configured? auth/hcaptcha-config)
               {:biff.auth/hcaptcha-secret   (constantly "secret")
                :biff.auth/hcaptcha-site-key nil})))
  (is (true? ((:biff.auth/captcha-configured? auth/turnstile-config)
              {:biff.auth/turnstile-secret   (constantly "secret")
               :biff.auth/turnstile-site-key "site-key"}))))
