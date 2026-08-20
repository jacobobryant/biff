(ns com.biffweb.authenticate.impl.util)

(defn email-valid? [_ctx email]
  (and (string? email)
       (re-matches #".+@.+\..+" email)
       (not (re-find #"\s" email))))

(defn new-code [_ctx length]
  (let [rng (java.security.SecureRandom.)]
    (format (str "%0" length "d")
            (.nextInt rng (dec (int (Math/pow 10 length)))))))

(defn new-link-token [_ctx n-bytes]
  (let [bytes (byte-array n-bytes)
        rng   (java.security.SecureRandom.)]
    (.nextBytes rng bytes)
    (apply str (map #(format "%02x" %) bytes))))
