(ns com.example.util)

(defn email-signin-enabled? [sys]
  (every? sys [:mailersend/api-key :recaptcha/site-key :recaptcha/secret-key]))
