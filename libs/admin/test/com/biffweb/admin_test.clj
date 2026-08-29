(ns com.biffweb.admin-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [com.biffweb.admin :as admin]
            [com.biffweb.admin.impl.alerts :as alerts]
            [com.biffweb.admin.impl.module :as module]
            [com.biffweb.admin.impl.profiling :as profiling]
            [com.biffweb.admin.impl.util :as util]
            [com.biffweb.admin.impl.users :as users]
            [taoensso.tufte :as tufte]
            [tick.core :as tick]))

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
    (let [m (admin/module {:biff.admin/get-usage-events (fn [_] [])})]
      (is (contains? m :biff.core/init))
      (is (contains? m :biff.background/tasks))
      (is (contains? m :biff.ring/routes))
      (is (= [profiling/wrap-profiling] (:biff.ring/base-middleware m)))
      (is (= [profiling/wrap-resolver-profiling]
             (:biff.graph/middleware m)))
      (is (fn? (:biff.core/init m)))))

  (testing "biff.core/init creates a pstats atom"
    (let [m           (admin/module {:biff.admin/get-usage-events (fn [_] [])})
          init-result ((:biff.core/init m) nil)]
      (is (contains? init-result :biff.admin/pstats))
      (is (instance? clojure.lang.Atom (:biff.admin/pstats init-result)))
      (is (= {} @(:biff.admin/pstats init-result))))))

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
          resp        (with-redefs [tick/now
                                    (constantly
                                     (tick/instant "2026-04-27T10:30:00Z"))]
                        (wrapped {:request-method    :get
                                  :biff.admin/pstats pstats-atom

                                  :reitit.core/match
                                  {:data     {:name ::test-route}
                                   :template "/test"}}))]
      (is (= 200 (:status resp)))
      (is (= ["2026-04-27"] (keys @pstats-atom)))
      (let [formatted (tufte/format-pstats
                       (get @pstats-atom "2026-04-27"))]
        (is (str/includes? formatted "GET :com.biffweb.admin-test/test-route"))
        (is (not (str/includes? formatted
                                ":GET :com.biffweb.admin-test/test-route"))))
      (is (plain-data? (#'profiling/pstats->stored-value
                        (get @pstats-atom "2026-04-27")))))))

(deftest wrap-resolver-profiling-test
  (testing "wraps resolver resolve function"
    (let [resolver {:biff.graph/id         :test/resolver
                    :biff.graph/resolve-fn (fn [{:biff.graph/keys [input]}]
                                             {:result (:value input)})}
          wrapped  (admin/wrap-resolver-profiling resolver)]
      (is (= :test/resolver (:biff.graph/id wrapped)))
      (is (fn? (:biff.graph/resolve-fn wrapped)))))

  (testing "wrapped resolver returns correct result"
    (let [resolver {:biff.graph/id         :test/resolver
                    :biff.graph/resolve-fn (fn [{:biff.graph/keys [input]}]
                                             {:result (:value input)})}
          wrapped  (admin/wrap-resolver-profiling resolver)
          ctx      {:biff.admin/pstats (atom nil)
                    :biff.graph/input  {:value 42}}
          result   ((:biff.graph/resolve-fn wrapped) ctx)]
      (is (= {:result 42} result)))))

(deftest flush-pstats-test
  (testing (str "flush-pstats! writes all day snapshots and keeps only "
                "the current day in memory")
    (let [stored      (atom {"2026-04-27" {:old true}})
          current     (sample-pstats "current")
          yesterday   (sample-pstats "existing")
          pstats-atom (atom {"2026-04-27" yesterday
                             "2026-04-28" current})
          ctx         {:biff.admin/pstats pstats-atom
                       :biff.core/kv-set  (fn [_ _ key value]
                                            (swap! stored assoc key value))}]
      (with-redefs [tick/now (constantly (tick/instant "2026-04-28T10:30:00Z"))]
        (profiling/flush-pstats! ctx))
      (is (= {"2026-04-28" current} @pstats-atom))
      (is (plain-data? (get @stored "2026-04-27")))
      (is (plain-data? (get @stored "2026-04-28")))
      (let [formatted (str (tufte/format-grouped-pstats
                            {"2026-04-27" (get @stored "2026-04-27")
                             "2026-04-28" (get @stored "2026-04-28")}))]
        (is (str/includes? formatted "2026-04-27"))
        (is (str/includes? formatted "2026-04-28"))
        (is (str/includes? formatted ":existing"))
        (is (str/includes? formatted ":current")))))

  (testing (str "flush-pstats! leaves the current day in memory and "
                "removes older days")
    (let [pstats-atom (atom {"2026-04-27" (sample-pstats "old")
                             "2026-04-28" (sample-pstats "current")
                             "2026-04-29" (sample-pstats "future")})
          ctx         {:biff.admin/pstats pstats-atom
                       :biff.core/kv-set  (fn [& _])}]
      (with-redefs [tick/now (constantly (tick/instant "2026-04-28T10:30:00Z"))]
        (profiling/flush-pstats! ctx))
      (is (= ["2026-04-28"] (keys @pstats-atom))))))

(deftest recent-pstats-data-test
  (testing (str "recent-pstats-data returns grouped persisted days with "
                "in-memory current-day override")
    (let [stored (into {}
                       (mapv (fn [[day label]]
                               [day (#'profiling/pstats->stored-value
                                     (sample-pstats label))])
                             [["2026-04-21" "too-old"]
                              ["2026-04-22" "day-1"]
                              ["2026-04-23" "day-2"]
                              ["2026-04-24" "day-3"]
                              ["2026-04-25" "day-4"]
                              ["2026-04-26" "day-5"]
                              ["2026-04-27" "day-6"]
                              ["2026-04-28" "day-7"]]))
          ctx    {:biff.admin/pstats
                  (atom {"2026-04-28" (sample-pstats "current-hour")})

                  :biff.core/kv-get (fn [_ _ key] (get stored key))}]
      (with-redefs [tick/now (constantly (tick/instant "2026-04-28T12:00:00Z"))]
        (let [formatted (str (tufte/format-grouped-pstats
                              (profiling/recent-pstats-data ctx)))]
          (is (str/includes? formatted ":day-1"))
          (is (str/includes? formatted ":current-hour"))
          (is (not (str/includes? formatted ":day-7")))
          (is (not (str/includes? formatted ":too-old")))))))

  (testing "recent-pstats-data deletes malformed stored values"
    (let [stored (atom {"2026-04-28" {:legacy true}

                        "2026-04-27"
                        (#'profiling/pstats->stored-value
                         (sample-pstats "day-6"))})
          ctx    {:biff.admin/pstats (atom {})
                  :biff.core/kv-get  (fn [_ _ key] (get @stored key))
                  :biff.core/kv-set  (fn [_ _ key value]
                                       (swap! stored assoc key value))}]
      (with-redefs [tick/now (constantly (tick/instant "2026-04-28T12:00:00Z"))]
        (let [formatted (str (tufte/format-grouped-pstats
                              (profiling/recent-pstats-data ctx)))]
          (is (str/includes? formatted ":day-6"))
          (is (nil? (get @stored "2026-04-28"))))))))

(deftest performance-dashboard-section-test
  (let [pstats-by-day {"2026-04-27" (sample-pstats "first-day")
                       "2026-04-28" (sample-pstats "second-day")}
        section       (with-redefs [profiling/recent-pstats-data
                                    (constantly pstats-by-day)]
                        (profiling/dashboard-section {}))
        pre-elements  (filterv #(and (vector? %)
                                     (some-> % first str
                                             (str/starts-with? ":pre.")))
                               (tree-seq coll? seq section))
        rendered      (pr-str section)]
    (is (= 2 (count pre-elements)))
    (is (str/includes? rendered "2026-04-27"))
    (is (str/includes? rendered "2026-04-28"))
    (is (str/includes? rendered ":first-day"))
    (is (str/includes? rendered ":second-day"))))

(deftest hourly-schedule-test
  (testing "hourly-schedule starts at the next UTC hour"
    (let [schedule (profiling/hourly-schedule-from
                    (java.time.ZonedDateTime/parse "2026-04-28T10:15:00Z"))]
      (is (= "2026-04-28T11:00Z"
             (str (first schedule))))
      (is (= "2026-04-28T12:00Z"
             (str (second schedule)))))))

(deftest get-route-id-test
  (testing "returns nil when no route match"
    (is (nil? (profiling/get-route-id {:request-method :get}))))

  (testing "uses route name when available"
    (is (= "GET :com.biffweb.admin-test/my-route"
           (profiling/get-route-id
            {:request-method    :get
             :reitit.core/match {:data     {:name ::my-route}
                                 :template "/test"}}))))

  (testing "falls back to template when no name"
    (is (= "POST /stuff/:id"
           (profiling/get-route-id
            {:request-method    :post
             :reitit.core/match {:data     {}
                                 :template "/stuff/:id"}})))))

(deftest impersonation-code-test
  (let [store            (atom {})
        kv-get           (fn [_ namespace key]
                           (some-> (get @store [namespace key])
                                   (update :generated-at
                                           #(java.util.Date/from %))))
        kv-set           (fn [_ namespace key value]
                           (swap! store assoc [namespace key] value))
        ctx              ((#'module/wrap-fx-handlers identity)
                          {:biff.core/kv-get  kv-get
                           :biff.core/kv-set  kv-set
                           :biff.stuff/params {:user-id ":user/one"}
                           :headers           {"host" "example.com"}
                           :scheme            :https})
        response         (#'users/generate-signin-code-handler ctx)
        [[_ code] entry] (first @store)]
    (is (= 303 (:status response)))
    (is (str/includes? (get-in response [:headers "location"])
                       "%2F_biff%2Fadmin%2Fsignin%2F"))
    (is (= :user/one (:user-id entry)))
    (is (inst? (:generated-at entry)))
    (is (= {:status  303
            :headers {"location" "/"}
            :session {:uid :user/one}}
           (#'users/signin-handler
            (assoc ctx :path-params {:code code} :session {}))))
    (is (nil? (kv-get ctx :biff.admin/signin-code code)))))

(deftest recent-errors-test
  (let [store  (atom {})
        kv-get (fn [_ namespace key] (get @store [namespace key]))
        kv-set (fn [_ namespace key value]
                 (swap! store assoc [namespace key] value))
        ctx    {:biff.admin/alert-email "ops@example.com"

                :biff.admin/alert-state
                (atom {:errors       []
                       :pending      []
                       :last-sent-at Double/NEGATIVE_INFINITY})

                :biff.admin/send-email (fn [_ _])
                :biff.core/kv-get      kv-get
                :biff.core/kv-set      kv-set}]
    (#'alerts/handle-error
     ctx {:level :error :error (ex-info "local" {})})
    (let [stored (kv-get ctx :biff.admin/errors "errors")
          host   (first (keys stored))
          remote {:message     "remote"
                  :stack-trace "remote trace"
                  :instant     (tick/>> (tick/now)
                                        (tick/new-duration 1 :hours))}
          stale  {:message     "stale"
                  :stack-trace "stale trace"
                  :instant     (tick/<< (tick/now)
                                        (tick/new-duration 73 :hours))}]
      (is (= ["local"] (mapv :message (get stored host))))
      (kv-set ctx :biff.admin/errors "errors"
              (assoc stored "remote-host" [stale remote]))
      (is (= ["local" "remote"]
             (mapv :message (#'alerts/recent-errors ctx)))))))

(deftest user-search-and-pagination-test
  (let [users (mapv (fn [index]
                      {:user-id index
                       :email   (str "user" index "@example.com")})
                    (range 60))]
    (is (= [5] (mapv :user-id (#'users/search-users users "USER5@"))))
    (is (= 1 (#'users/parse-page "invalid")))
    (let [page (pr-str (users/dashboard-section
                        {:biff.stuff/params {:user-page "2"}}
                        users nil nil))]
      (is (str/includes? page "Page 2 of 2"))
      (is (str/includes? page "Copy sign-in link"))
      (is (str/includes? page "user59@example.com"))
      (is (not (str/includes? page "user0@example.com"))))
    (let [page (pr-str (users/dashboard-section
                        {:biff.stuff/params {}}
                        users nil "https://example.com/signin/code"))]
      (is (str/includes? page "Sign-in link copied to clipboard."))
      (is (str/includes? page ":data-clipboard"))
      (is (str/includes? page ":data-clipboard-on-load"))
      (is (not (str/includes? page
                              "Create sign-in link"))))
    (let [body (:body (users/page
                       {:biff.admin/get-users (constantly users)

                        :biff.stuff/params
                        {:signin-url "https://example.com/signin/code"}}))]
      (is (str/includes? body "Sign-in link copied to clipboard."))
      (is (str/includes? body "/_biff/admin/main.js"))
      (is (not (str/includes? body "<<>>")))
      (is (not (str/includes? body "&lt;&lt;&gt;&gt;"))))))

(deftest wrap-admin-access-test
  (let [handler (util/wrap-admin-access (constantly {:status 200}))]
    (is (= 401 (:status (handler {}))))
    (is (= 401 (:status (handler {:biff.admin/admin-user-id :admin
                                  :session                  {}}))))
    (is (= 403
           (:status
            (handler {:biff.admin/admin-user-id :admin
                      :session                  {:uid :someone-else}}))))
    (is (= 200 (:status (handler {:biff.admin/admin-user-id :admin
                                  :session                  {:uid :admin}}))))
    (is (str/includes? (:body (handler {:session {:uid :admin}}))
                       "Admin Setup"))))

(deftest alerts-lifecycle-test
  (testing "the alerts module adds alert-state to ctx"
    (let [module (admin/module {:biff.admin/get-usage-events (fn [_] [])})
          result ((:biff.core/start module) {})]
      (is (contains? result :biff.admin/alert-state))
      (is (instance? clojure.lang.Atom (:biff.admin/alert-state result)))
      (is (= [] (:errors @(:biff.admin/alert-state result))))
      ((:biff.core/stop module) result))))
