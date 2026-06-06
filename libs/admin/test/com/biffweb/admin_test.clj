(ns com.biffweb.admin-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [com.biffweb.admin :as admin]
            [taoensso.tufte :as tufte]
            [tick.core :as t]))

(defn- sample-pstats [id]
  (second (tufte/profiled {} (tufte/p (keyword id) :ok))))

(defn- plain-data? [x]
  (cond
    (or (nil? x)
        (string? x)
        (boolean? x)
        (number? x)
        (keyword? x)
        (symbol? x))
    true

    (record? x)
    false

    (map? x)
    (every? true? (mapcat (fn [[k v]] [(plain-data? k) (plain-data? v)]) x))

    (vector? x)
    (every? plain-data? x)

    (list? x)
    (every? plain-data? x)

    (set? x)
    (every? plain-data? x)

    :else
    false))

(deftest module-test
  (testing "module returns expected keys"
    (let [m (admin/module {:biff.admin/get-user-events (fn [_] [])})]
      (is (contains? m :biff.core/init))
      (is (contains? m :biff.background/tasks))
      (is (contains? m :biff.ring/routes))
      (is (= [admin/wrap-profiling] (:biff.ring/base-middleware m)))
      (is (= [admin/wrap-resolver-profiling] (:biff.graph/middleware m)))
      (is (fn? (:biff.core/init m)))))

  (testing "biff.core/init creates pstats and signin-codes atoms"
    (let [m           (admin/module {:biff.admin/get-user-events (fn [_] [])})
          init-result ((:biff.core/init m) nil)]
      (is (contains? init-result :biff.admin/pstats))
      (is (instance? clojure.lang.Atom (:biff.admin/pstats init-result)))
      (is (= {} @(:biff.admin/pstats init-result)))
      (is (contains? init-result :biff.admin/signin-codes))
      (is (instance? clojure.lang.Atom (:biff.admin/signin-codes init-result))))))

(deftest wrap-profiling-test
  (testing "wrap-profiling passes through when no pstats"
    (let [handler (fn [_] {:status 200})
          wrapped (admin/wrap-profiling handler)
          resp    (wrapped {:request-method :get})]
      (is (= 200 (:status resp)))))

  (testing "wrap-profiling passes through when no route-id"
    (let [handler (fn [_] {:status 200})
          wrapped (admin/wrap-profiling handler)
          resp    (wrapped {:request-method    :get
                            :biff.admin/pstats (atom nil)})]
      (is (= 200 (:status resp)))))

  (testing "wrap-profiling profiles when route-id available"
    (let [pstats-atom (atom nil)
          handler     (fn [_] {:status 200})
          wrapped     (admin/wrap-profiling handler)
          resp        (with-redefs [t/now (constantly (t/instant "2026-04-27T10:30:00Z"))]
                        (wrapped {:request-method    :get
                                  :biff.admin/pstats pstats-atom
                                  :reitit.core/match {:data     {:name ::test-route}
                                                      :template "/test"}}))]
      (is (= 200 (:status resp)))
      (is (= ["2026-04-27"] (keys @pstats-atom)))
      (is (plain-data? (#'admin/pstats->stored-value (get @pstats-atom "2026-04-27")))))))

(deftest wrap-resolver-profiling-test
  (testing "wraps resolver resolve function"
    (let [resolver {:id      :test/resolver
                    :resolve (fn [_ctx input] {:result (:value input)})}
          wrapped  (admin/wrap-resolver-profiling resolver)]
      (is (= :test/resolver (:id wrapped)))
      (is (fn? (:resolve wrapped)))))

  (testing "wrapped resolver returns correct result"
    (let [resolver {:id      :test/resolver
                    :resolve (fn [_ctx input] {:result (:value input)})}
          wrapped  (admin/wrap-resolver-profiling resolver)
          ctx      {:biff.admin/pstats (atom nil)}
          result   ((:resolve wrapped) ctx {:value 42})]
      (is (= {:result 42} result)))))

(deftest flush-pstats-test
  (testing "flush-pstats! writes all day snapshots and keeps only the current day in memory"
    (let [stored      (atom {"2026-04-27" {:old true}})
          current     (sample-pstats "current")
          yesterday   (sample-pstats "existing")
          pstats-atom (atom {"2026-04-27" yesterday
                             "2026-04-28" current})
          ctx         {:biff.admin/pstats pstats-atom
                       :biff.kv/set-value (fn [_ _ key value] (swap! stored assoc key value))}]
      (with-redefs [t/now (constantly (t/instant "2026-04-28T10:30:00Z"))]
        (#'admin/flush-pstats! ctx))
      (is (= {"2026-04-28" current} @pstats-atom))
      (is (plain-data? (get @stored "2026-04-27")))
      (is (plain-data? (get @stored "2026-04-28")))
      (let [formatted (str (tufte/format-grouped-pstats {"2026-04-27" (get @stored "2026-04-27")
                                                         "2026-04-28" (get @stored "2026-04-28")}))]
        (is (str/includes? formatted "2026-04-27"))
        (is (str/includes? formatted "2026-04-28"))
        (is (str/includes? formatted ":existing"))
        (is (str/includes? formatted ":current")))))

  (testing "flush-pstats! leaves the current day in memory and removes older days"
    (let [pstats-atom (atom {"2026-04-27" (sample-pstats "old")
                             "2026-04-28" (sample-pstats "current")
                             "2026-04-29" (sample-pstats "future")})
          ctx         {:biff.admin/pstats pstats-atom
                       :biff.kv/set-value (fn [& _])}]
      (with-redefs [t/now (constantly (t/instant "2026-04-28T10:30:00Z"))]
        (#'admin/flush-pstats! ctx))
      (is (= ["2026-04-28"] (keys @pstats-atom))))))

(deftest recent-pstats-data-test
  (testing "recent-pstats-data returns grouped persisted days with in-memory current-day override"
    (let [stored (into {}
                       [["2026-04-21" (#'admin/pstats->stored-value (sample-pstats "too-old"))]
                        ["2026-04-22" (#'admin/pstats->stored-value (sample-pstats "day-1"))]
                        ["2026-04-23" (#'admin/pstats->stored-value (sample-pstats "day-2"))]
                        ["2026-04-24" (#'admin/pstats->stored-value (sample-pstats "day-3"))]
                        ["2026-04-25" (#'admin/pstats->stored-value (sample-pstats "day-4"))]
                        ["2026-04-26" (#'admin/pstats->stored-value (sample-pstats "day-5"))]
                        ["2026-04-27" (#'admin/pstats->stored-value (sample-pstats "day-6"))]
                        ["2026-04-28" (#'admin/pstats->stored-value (sample-pstats "day-7"))]])
          ctx    {:biff.admin/pstats (atom {"2026-04-28" (sample-pstats "current-hour")})
                  :biff.kv/get-value (fn [_ _ key] (get stored key))}]
      (with-redefs [t/now (constantly (t/instant "2026-04-28T12:00:00Z"))]
        (let [formatted (str (tufte/format-grouped-pstats (#'admin/recent-pstats-data ctx)))]
          (is (str/includes? formatted ":day-1"))
          (is (str/includes? formatted ":current-hour"))
          (is (not (str/includes? formatted ":day-7")))
          (is (not (str/includes? formatted ":too-old")))))))

  (testing "recent-pstats-data deletes malformed stored values"
    (let [stored (atom {"2026-04-28" {:legacy true}
                        "2026-04-27" (#'admin/pstats->stored-value (sample-pstats "day-6"))})
          ctx    {:biff.admin/pstats (atom {})
                  :biff.kv/get-value (fn [_ _ key] (get @stored key))
                  :biff.kv/set-value (fn [_ _ key value] (swap! stored assoc key value))}]
      (with-redefs [t/now (constantly (t/instant "2026-04-28T12:00:00Z"))]
        (let [formatted (str (tufte/format-grouped-pstats (#'admin/recent-pstats-data ctx)))]
          (is (str/includes? formatted ":day-6"))
          (is (nil? (get @stored "2026-04-28"))))))))

(deftest hourly-schedule-test
  (testing "hourly-schedule starts at the next UTC hour"
    (let [schedule (#'admin/hourly-schedule-from
                    (java.time.ZonedDateTime/parse "2026-04-28T10:15:00Z"))]
      (is (= "2026-04-28T11:00Z"
             (str (first schedule))))
      (is (= "2026-04-28T12:00Z"
             (str (second schedule)))))))

(deftest default-get-route-id-test
  (testing "returns nil when no route match"
    (is (nil? (admin/default-get-route-id {:request-method :get}))))

  (testing "uses route name when available"
    (is (= "GET :com.biffweb.admin-test/my-route"
           (admin/default-get-route-id
            {:request-method    :get
             :reitit.core/match {:data     {:name ::my-route}
                                 :template "/test"}}))))

  (testing "falls back to template when no name"
    (is (= "POST /stuff/:id"
           (admin/default-get-route-id
            {:request-method    :post
             :reitit.core/match {:data     {}
                                 :template "/stuff/:id"}})))))

(deftest health-endpoint-test
  (testing "module routes include health endpoint"
    (let [m            (admin/module {:biff.admin/get-user-events (fn [_] [])})
          routes       (:biff.ring/routes m)
          ;; Routes structure: [prefix {middleware} ["/health" ...] ...]
          health-route (some (fn [r] (when (and (vector? r) (= "/health" (first r))) r))
                             (rest (rest routes)))]
      (is (some? health-route)))))

(deftest use-alerts-test
  (testing "use-alerts adds errors-atom and alert-state to ctx"
    (let [ctx    {:biff.core/stop []}
          result (admin/use-alerts ctx)]
      (is (contains? result :biff.admin/errors-atom))
      (is (contains? result :biff.admin/alert-state))
      (is (instance? clojure.lang.Atom (:biff.admin/errors-atom result)))
      ;; Clean up
      (doseq [f (:biff.core/stop result)] (f)))))
