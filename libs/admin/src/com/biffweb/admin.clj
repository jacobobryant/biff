(ns com.biffweb.admin
  "Admin dashboard library for Biff web applications.

   Provides:
   - Performance metrics via tufte profiling
   - Usage metrics (DAU, WAU, signups, revenue)
   - User list with impersonation
   - Resource usage monitoring (CPU, RAM, disk)
   - Error alerting via email with rate limiting
   - Health check endpoint
   - Middleware for HTTP handler profiling
   - Helper for wrapping biff.graph resolvers with profiling"
  (:require [com.biffweb.admin.impl.ui :as ui]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [com.biffweb.core :as biff.core]
            [taoensso.tufte :as tufte]
            [taoensso.telemere :as tel]
            [taoensso.telemere.tools-logging :as tel.tl]
            [tick.core :as t])
  (:import [java.security SecureRandom]
           [java.io File]
           [java.time ZoneOffset ZonedDateTime]
           [java.time.temporal ChronoUnit]))

(biff.core/register
 {:biff.admin/alert-email        'string?
  :biff.admin/alert-state        [:fn #(instance? clojure.lang.IAtom %)]
  :biff.admin/errors-atom        [:fn #(instance? clojure.lang.IAtom %)]
  :biff.admin/get-revenue-events 'ifn?
  :biff.admin/get-route-id       'ifn?
  :biff.admin/get-user-events    'ifn?
  :biff.admin/get-users          'ifn?
  :biff.admin/healthy?           'ifn?
  :biff.admin/pstats             [:fn #(instance? clojure.lang.IAtom %)]
  :biff.admin/send-email         'ifn?
  :biff.admin/signin-codes       [:fn #(instance? clojure.lang.IAtom %)]
  :biff.admin/user-id            'some?})

;; ============================================================
;; Profiling helpers
;; ============================================================

(defn- pstats-day-key [now]
  (str (t/date (t/in now (t/zone "UTC")))))

(defn- profile!
  "Execute f (a zero-arg function) under tufte profiling with the given id.
   Merges the resulting pstats into the :biff.admin/pstats atom."
  [ctx id f]
  (let [pstats (:biff.admin/pstats ctx)]
    (if (and pstats id)
      (let [[result pstats-data]
            (tufte/profiled {} (tufte/p (keyword id) (f)))]
        (when pstats-data
          (let [day-key (pstats-day-key (t/now))]
            (swap! pstats update day-key
                   (fn [existing]
                     (if existing
                       (tufte/merge-pstats existing pstats-data)
                       pstats-data)))))
        result)
      (f))))

(def ^:private pstats-kv-namespace :biff.admin/pstats)

(defn- stored-pstats? [value]
  (and (map? value)
       (not (record? value))
       (map? (:stats value))
       (map? (:clock value))))

(defn- pstats->stored-value [pstats]
  (into {} @pstats))

(defn- get-stored-pstats [{:keys [biff.kv/get-value biff.kv/set-value] :as ctx} day-key]
  (when get-value
    (let [value (get-value ctx pstats-kv-namespace day-key)]
      (cond
        (nil? value)
        nil

        (stored-pstats? value)
        value

        :else
        (do
          (when set-value
            (set-value ctx pstats-kv-namespace day-key nil))
          nil)))))

(defn- recent-day-keys [now]
  (let [today     (t/date (t/in now (t/zone "UTC")))
        start-day (t/<< today (t/new-period 6 :days))]
    (mapv str (take 7 (iterate #(t/>> % (t/new-period 1 :days)) start-day)))))

(defn- current-pstats-data [pstats day-keys]
  (let [day-set (set day-keys)]
    (reduce-kv (fn [acc day day-pstats]
                 (if (and (contains? day-set day) day-pstats)
                   (assoc acc day (pstats->stored-value day-pstats))
                   acc))
               {}
               @pstats)))

(defn- flush-pstats! [{:keys [biff.admin/pstats biff.kv/set-value] :as ctx}]
  (when (and pstats set-value)
    (let [current-day       (pstats-day-key (t/now))
          [all-pstats _new] (swap-vals! pstats #(select-keys % [current-day]))]
      (doseq [[day day-pstats] all-pstats
              :when            day-pstats]
        (set-value ctx pstats-kv-namespace day (pstats->stored-value day-pstats))))))

(defn- hourly-schedule-from [now]
  (let [start (-> now
                  (.truncatedTo ChronoUnit/HOURS)
                  (.plusHours 1))]
    (iterate #(.plusHours ^ZonedDateTime % 1) start)))

(defn- hourly-schedule []
  (hourly-schedule-from (ZonedDateTime/now ZoneOffset/UTC)))

(defn- recent-pstats-data [{:keys [biff.admin/pstats] :as ctx}]
  (let [day-keys  (recent-day-keys (t/now))
        persisted (reduce (fn [acc day]
                            (if-some [value (get-stored-pstats ctx day)]
                              (assoc acc day value)
                              acc))
                          (array-map)
                          day-keys)
        current   (if pstats
                    (current-pstats-data pstats day-keys)
                    {})]
    (not-empty (merge persisted current))))

(defn default-get-route-id
  "Default route ID extraction. Returns e.g. \"POST /stuff/:id\"."
  [ctx]
  (let [method     (some-> (:request-method ctx) name str/upper-case)
        match      (:reitit.core/match ctx)
        route-name (some-> match :data :name)
        route-path (some-> match :template)]
    (when (or route-name route-path)
      (str method " " (or route-name route-path)))))

(defn wrap-profiling
  "Ring middleware that wraps the handler in a tufte/p call for profiling.
   Merges pstats into the :biff.admin/pstats atom after each request."
  [handler]
  (fn [{:biff.admin/keys [get-route-id] :as ctx}]
    (let [route-id ((or get-route-id default-get-route-id) ctx)]
      (profile! ctx route-id #(handler ctx)))))

(defn wrap-resolver-profiling
  "Middleware function for biff.graph/build-index.
   Wraps a resolver's :resolve function with tufte profiling."
  [resolver]
  (let [id           (:id resolver)
        orig-resolve (:resolve resolver)]
    (assoc resolver :resolve
           (fn [ctx input]
             (profile! ctx (str id) #(orig-resolve ctx input))))))

;; ============================================================
;; Usage metrics
;; ============================================================

(defn- day-key
  "Convert an instant to a tick/date in the given timezone."
  [instant tz]
  (t/date (t/in instant tz)))

(defn- date-range
  "Returns a sequence of dates from start-date to end-date (inclusive)."
  [start-date end-date]
  (->> (iterate #(t/>> % (t/new-period 1 :days)) start-date)
       (take-while #(t/<= % end-date))
       vec))

(defn- compute-dau
  "Compute Daily Active Users from user events.
   Returns a sorted map of date -> count.
   Filters out events older than 30 days."
  [events tz now]
  (let [today  (t/date (t/in now tz))
        cutoff (t/<< today (t/new-period 30 :days))]
    (->> events
         (filter #(t/<= cutoff (day-key (:instant %) tz)))
         (group-by #(day-key (:instant %) tz))
         (reduce-kv (fn [m day evts]
                      (assoc m day (count (set (map :user-id evts)))))
                    (sorted-map)))))

(defn- compute-wau
  "Compute Weekly Active Users (rolling 7-day window).
   For each day in the last 30 days, count unique users from that day and previous 6 days.
   Filters out events older than 37 days."
  [events tz now]
  (let [today         (t/date (t/in now tz))
        cutoff        (t/<< today (t/new-period 37 :days))
        events        (filter #(t/<= cutoff (day-key (:instant %) tz)) events)
        events-by-day (group-by #(day-key (:instant %) tz) events)
        end-date      today
        start-date    (t/<< today (t/new-period 29 :days))
        days          (date-range start-date end-date)]
    (into (sorted-map)
          (map (fn [day]
                 (let [window-start (t/<< day (t/new-period 6 :days))
                       window-days  (date-range window-start day)
                       unique-users (->> window-days
                                         (mapcat #(get events-by-day %))
                                         (keep :user-id)
                                         set
                                         count)]
                   [day unique-users])))
          days)))

(defn- compute-daily-signups
  "Compute daily signups from user joined-at dates.
   Returns a sorted map of date -> count."
  [users tz now]
  (let [today  (t/date (t/in now tz))
        cutoff (t/<< today (t/new-period 30 :days))]
    (->> users
         (filter :joined-at)
         (filter #(t/<= cutoff (day-key (:joined-at %) tz)))
         (group-by #(day-key (:joined-at %) tz))
         (reduce-kv (fn [m day u]
                      (assoc m day (count u)))
                    (sorted-map)))))

(defn- compute-daily-revenue
  "Compute daily revenue from revenue events.
   Filters out events older than 30 days."
  [events tz now]
  (let [today  (t/date (t/in now tz))
        cutoff (t/<< today (t/new-period 30 :days))]
    (->> events
         (filter #(t/<= cutoff (day-key (:instant %) tz)))
         (group-by #(day-key (:instant %) tz))
         (reduce-kv (fn [m day evts]
                      (assoc m day (reduce + 0 (map :revenue evts))))
                    (sorted-map)))))

;; ============================================================
;; Resource usage
;; ============================================================

(defn- get-resource-usage
  "Returns a map of current system resource usage."
  []
  (let [runtime    (Runtime/getRuntime)
        total-mem  (.totalMemory runtime)
        free-mem   (.freeMemory runtime)
        max-mem    (.maxMemory runtime)
        used-mem   (- total-mem free-mem)
        ;; Disk usage for root filesystem
        root       (File. "/")
        disk-total (.getTotalSpace root)
        disk-free  (.getUsableSpace root)
        disk-used  (- disk-total disk-free)
        ;; CPU - use /proc/loadavg on Linux
        load-avg   (try
                     (let [content (slurp "/proc/loadavg")]
                       (Double/parseDouble (first (str/split content #"\s+"))))
                     (catch Exception _
                       (try
                         (.getSystemLoadAverage
                          (java.lang.management.ManagementFactory/getOperatingSystemMXBean))
                         (catch Exception _ -1.0))))
        cpu-count  (.availableProcessors runtime)]
    {:ram-used   used-mem
     :ram-total  max-mem
     :ram-pct    (if (pos? max-mem) (* 100.0 (/ used-mem (double max-mem))) 0)
     :disk-used  disk-used
     :disk-total disk-total
     :disk-pct   (if (pos? disk-total) (* 100.0 (/ disk-used (double disk-total))) 0)
     :cpu-load   load-avg
     :cpu-count  cpu-count}))

;; ============================================================
;; Error alerting
;; ============================================================

(defn- generate-secure-code
  "Generate a URL-safe random code string."
  [n-bytes]
  (let [sr (SecureRandom.)
        bs (byte-array n-bytes)]
    (.nextBytes sr bs)
    (str/replace
     (.encodeToString (java.util.Base64/getUrlEncoder) bs)
     #"=" "")))

(defn- handle-error
  "Telemere signal handler for error alerting. Batches errors and sends email
   alerts with rate limiting."
  [{:biff.admin/keys [send-email errors-atom alert-state alert-email]
    :as              ctx}
   signal]
  (when (= (:level signal) :error)
    (let [max-errors         20
          rate-limit-seconds (* 60 5)
          now-seconds        (/ (System/nanoTime) (* 1000 1000 1000.0))
          formatted          (try
                               ((tel/format-signal-fn {}) signal)
                               (catch Exception e
                                 (str "Error formatting signal: " e "\n" (pr-str signal))))
          error-entry        {:message     (or (some-> signal :error .getMessage) "Unknown error")
                              :stack-trace formatted
                              :timestamp   (t/now)}]
      ;; Store in errors atom (keep last 20)
      (when errors-atom
        (swap! errors-atom (fn [errors]
                             (vec (take-last max-errors (conj errors error-entry))))))
      ;; Handle batched email alerting (only if both send-email and alert-email are set)
      (when (and send-email alert-email alert-state)
        (let [{:keys [batch]} (swap! alert-state
                                     (fn [{:keys [pending last-sent-at]}]
                                       (let [pending (conj (or pending []) formatted)]
                                         (if (< rate-limit-seconds (- now-seconds (or last-sent-at 0)))
                                           {:batch        pending
                                            :pending      []
                                            :last-sent-at now-seconds}
                                           {:pending      pending
                                            :last-sent-at (or last-sent-at 0)}))))]
          (when (not-empty batch)
            (try
              (let [error-text (str/join "\n\n---\n\n" (take-last max-errors batch))
                    preview    (subs error-text 0 (min 1000 (count error-text)))]
                (send-email ctx
                            {:to      alert-email
                             :subject "Application error alert"
                             :text    error-text
                             :html    (str "<pre>" preview "</pre>")}))
              (catch Exception e
                ;; Log but don't re-throw to avoid infinite loops
                (binding [*out* *err*]
                  (println "Failed to send error alert email:" (.getMessage e)))))))))))

(defn use-alerts
  "Biff component that sets up error alerting via telemere.
   Calls tools-logging->telemere! to capture all logging.
   Stores recent exceptions in an atom for the admin dashboard.

   Expects :biff.admin/send-email in ctx for email alerting."
  [ctx]
  (tel.tl/tools-logging->telemere!)
  (let [errors-atom (atom [])
        alert-state (atom {:pending [] :last-sent-at 0})
        ctx         (assoc ctx
                           :biff.admin/errors-atom errors-atom
                           :biff.admin/alert-state alert-state)]
    (tel/add-handler! :biff.admin/alerts
                      (fn [signal] (handle-error ctx signal)))
    (update ctx :biff.core/stop conj #(tel/remove-handler! :biff.admin/alerts))))

;; ============================================================
;; Health check
;; ============================================================

(defn- health-handler
  [{:biff.admin/keys [healthy?] :as ctx}]
  (let [healthy (if healthy?
                  (try (healthy? ctx) (catch Exception _ false))
                  true)]
    {:status  (if healthy 200 503)
     :headers {"content-type" "text/plain"}
     :body    (if healthy "healthy" "unhealthy")}))

;; ============================================================
;; Impersonation / sign-in codes
;; ============================================================

(defn- base-url-from-request
  "Infer the base URL from an incoming request."
  [ctx]
  (let [scheme (or (some-> ctx :headers (get "x-forwarded-proto"))
                   (name (or (:scheme ctx) :http)))
        host   (or (some-> ctx :headers (get "x-forwarded-host"))
                   (get-in ctx [:headers "host"])
                   "localhost")]
    (str scheme "://" host)))

(defn- generate-signin-code-handler
  [{:biff.admin/keys [signin-codes] :as ctx}]
  (let [user-id-str (or (get-in ctx [:params :user-id])
                        (get-in ctx [:query-params "user-id"]))
        code        (generate-secure-code 32)
        base        (base-url-from-request ctx)]
    (swap! signin-codes assoc code {:user-id      (edn/read-string user-id-str)
                                    :generated-at (t/now)})
    {:status  200
     :headers {"content-type" "application/json"}
     :body    (str "{\"url\":\"" base "/_biff/admin/signin/" code "\"}")}))

(defn- signin-handler
  [{:biff.admin/keys [signin-codes]
    :keys            [path-params session]
    :as              _ctx}]
  (let [code   (:code path-params)
        entry  (get @signin-codes code)
        now    (t/now)
        valid? (and entry
                    (t/< (t/between (:generated-at entry) now)
                         (t/new-duration 5 :minutes)))]
    (if valid?
      (do
        (swap! signin-codes dissoc code)
        {:status  302
         :headers {"location" "/"}
         :session (assoc session :uid (:user-id entry))})
      {:status  401
       :headers {"content-type" "text/plain"}
       :body    "Unauthorized"})))

;; ============================================================
;; Admin routes
;; ============================================================

(defn- wrap-admin-access
  "Middleware that checks admin access. Shows setup page if :biff.admin/user-id
   is not set, returns 403 if UIDs don't match, otherwise calls handler."
  [handler]
  (fn [{:biff.admin/keys [user-id] :keys [session] :as ctx}]
    (let [current-uid (str (:uid session))
          admin-uid   (str user-id)]
      (cond
        (str/blank? admin-uid)
        (ui/admin-page "Admin Setup"
                       [:div
                        (ui/heading "Admin Setup")
                        [:p.mb-4 ":biff.admin/user-id is not set. Your current user ID is:"]
                        [:div.flex.items-center.gap-2.mb-4
                         [:code.bg-gray-100.p-2.rounded.text-sm.break-all {:id "uid-display"} current-uid]
                         [:button.bg-blue-600.text-white.px-3.py-1.rounded.text-sm.cursor-pointer
                          {:onclick (str "navigator.clipboard.writeText('" current-uid "');"
                                         "this.textContent='Copied!';"
                                         "setTimeout(()=>this.textContent='Copy',2000)")}
                          "Copy"]]
                        [:p.text-sm.text-gray-600
                         "Set :biff.admin/user-id to enable the admin dashboard."]])

        (or (str/blank? current-uid)
            (not= current-uid admin-uid))
        {:status 403 :headers {"content-type" "text/html"} :body "<h1>Forbidden</h1>"}

        :else
        (handler ctx)))))

(defn- admin-dashboard-content
  [{:biff.admin/keys [get-user-events get-revenue-events get-users errors-atom]
    :as              ctx}
   timezone]
  (let [tz                 (try (t/zone timezone) (catch Exception _ (t/zone "UTC")))
        now                (t/now)
        user-events        (when get-user-events (get-user-events ctx))
        revenue-events     (when get-revenue-events (get-revenue-events ctx))
        users              (when get-users (get-users ctx))
        dau                (compute-dau (or user-events []) tz now)
        wau                (compute-wau (or user-events []) tz now)
        daily-signups      (when users (compute-daily-signups users tz now))
        daily-revenue      (when revenue-events (compute-daily-revenue revenue-events tz now))
        pstats-data        (recent-pstats-data ctx)
        pstats-formatted   (some-> pstats-data tufte/format-grouped-pstats)
        recent-days        (->> (keys dau) (take-last 30))
        resource-usage     (get-resource-usage)
        errors             (when errors-atom @errors-atom)
        anti-forgery-token (:anti-forgery-token ctx)]
    (ui/admin-fragment
     [:div
      ;; Usage Metrics
      (ui/section "Usage Metrics"
                  (when (seq recent-days)
                    (ui/metrics-table recent-days dau wau daily-signups daily-revenue))
                  (when-not (seq recent-days)
                    [:p.text-gray-500 "No activity data available."]))

      ;; User List
      (ui/section "Users"
                  (if (seq users)
                    (ui/users-table users anti-forgery-token)
                    [:p.text-gray-500 "No user data available."]))

      ;; Performance Metrics
      (ui/section "Performance Metrics"
                  (if pstats-formatted
                    [:pre.bg-gray-100.p-4.rounded.text-xs.overflow-x-auto
                     (str pstats-formatted)]
                    [:p.text-gray-500 "No performance data collected yet."]))

      ;; Resource Usage
      (ui/section "Resource Usage"
                  (ui/resource-usage-table resource-usage))

      ;; Recent Exceptions
      (when errors-atom
        (ui/section "Recent Exceptions"
                    [:div
                     [:button.bg-red-600.text-white.px-3.py-1.rounded.text-sm.cursor-pointer.mb-4
                      {:onclick (str "fetch('/_biff/admin/test-alert', {"
                                     "method: 'POST',"
                                     "headers: {'Content-Type': 'application/x-www-form-urlencoded'}"
                                     (when anti-forgery-token
                                       (str ",body: '__anti-forgery-token=' + encodeURIComponent('" anti-forgery-token "')"))
                                     "}).then(() => {"
                                     "this.textContent='Alert sent!';"
                                     "setTimeout(() => { this.textContent='Test alert'; location.reload(); }, 2000);"
                                     "}).catch(() => {"
                                     "this.textContent='Alert sent!';"
                                     "setTimeout(() => { this.textContent='Test alert'; location.reload(); }, 2000);"
                                     "});")}
                      "Test alert"]
                     (if (seq errors)
                       (ui/exceptions-table errors)
                       [:p.text-gray-500 "No exceptions recorded."])]))])))

(defn- admin-dashboard
  [_ctx]
  (ui/admin-page "Admin"
                 [:div
                  (ui/heading "Admin")
                  [:div {:id "admin-content"}
                   [:p.text-gray-500 "Loading..."]]
                  [:script
                   (str "document.addEventListener('DOMContentLoaded', function() {"
                        "  var tz = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';"
                        "  fetch('/_biff/admin/content?timezone=' + encodeURIComponent(tz))"
                        "    .then(function(r) { return r.text(); })"
                        "    .then(function(html) { document.getElementById('admin-content').innerHTML = html; });"
                        "});")]]))

(defn- admin-content-handler
  [ctx]
  (let [timezone (or (get-in ctx [:params :timezone])
                     (get-in ctx [:query-params "timezone"])
                     "UTC")]
    (admin-dashboard-content ctx timezone)))

(defn- stacktrace-page-handler
  [{:biff.admin/keys [errors-atom] :as ctx}]
  (let [index  (try (Integer/parseInt (or (get-in ctx [:params :index])
                                          (get-in ctx [:query-params "index"])
                                          (get-in ctx [:path-params :index])
                                          "0"))
                    (catch Exception _ 0))
        errors (when errors-atom @errors-atom)
        error  (get (vec errors) index)]
    (if error
      (ui/admin-page "Stack Trace"
                     [:div
                      (ui/heading "Stack Trace")
                      [:p.text-sm.text-gray-600.mb-2 (str "Error at " (:timestamp error))]
                      [:p.font-semibold.mb-4 (:message error)]
                      [:button.bg-blue-600.text-white.px-4.py-2.rounded.mb-4.cursor-pointer
                       {:onclick (str "navigator.clipboard.writeText(document.getElementById('stacktrace').textContent);"
                                      "this.textContent='Copied!';"
                                      "setTimeout(()=>this.textContent='Copy to clipboard',2000)")}
                       "Copy to clipboard"]
                      [:pre#stacktrace.bg-gray-100.p-4.rounded.text-xs.overflow-x-auto.whitespace-pre-wrap
                       (:stack-trace error)]])
      {:status 404 :headers {"content-type" "text/plain"} :body "Not found"})))

(defn- wrap-admin-params
  "Middleware that merges admin parameters into the request.
   Used with Reitit vector syntax: [wrap-admin-params params]."
  [handler params]
  (fn [ctx]
    (handler (merge ctx params))))

(defn- test-alert-handler
  "Handler that throws an unhandled exception to test error alerting."
  [_ctx]
  (throw (ex-info "Test alert from admin dashboard" {:type :test-alert})))

;; ============================================================
;; Module
;; ============================================================

(defn module
  "Creates a biff.admin module. Takes a map of options:
   - :biff.admin/get-user-events - fn [ctx] -> [{:user-id ... :instant ...} ...]
   - :biff.admin/get-revenue-events - (optional) fn [ctx] -> [{:revenue ... :instant ...} ...]
   - :biff.admin/get-users - (optional) fn [ctx] -> [{:user-id ... :joined-at ... :email ...} ...]
   - :biff.admin/get-route-id - (optional) fn [ctx] -> string, overrides default route ID extraction
   - :biff.admin/healthy? - (optional) fn [ctx] -> truthy, for health endpoint

   Returns a module map with :biff.ring/routes, :biff.ring/base-middleware,
   :biff.graph/middleware, and :biff.core/init."
  [params]
  {:biff.core/init            (fn [_modules-var]
                                {:biff.admin/pstats       (atom {})
                                 :biff.admin/signin-codes (atom {})})
   :biff.background/tasks     [{:schedule hourly-schedule
                                :task     flush-pstats!}]
   :biff.ring/routes          ["/_biff/admin" {:middleware [[wrap-admin-params params]]}
                               ["/health" {:get  health-handler
                                           :name ::health}]
                               ["/signin/:code" {:get  signin-handler
                                                 :name ::signin}]
                               ["" {:middleware [wrap-admin-access]}
                                ["" {:get  admin-dashboard
                                     :name ::dashboard}]
                                ["/content" {:get  admin-content-handler
                                             :name ::content}]
                                ["/stacktrace/:index" {:get  stacktrace-page-handler
                                                       :name ::stacktrace}]
                                ["/generate-signin-code" {:post generate-signin-code-handler
                                                          :name ::generate-signin-code}]
                                ["/test-alert" {:post test-alert-handler
                                                :name ::test-alert}]]]
   :biff.ring/base-middleware [wrap-profiling]
   :biff.graph/middleware     [wrap-resolver-profiling]})
