(ns com.biffweb.admin.impl.profiling
  (:require [clojure.string :as str]
            [com.biffweb.admin.impl.ui :as ui]
            [com.biffweb.admin.impl.util :as util]
            [taoensso.encore.stats :as stats]
            [taoensso.tufte :as tufte]
            [tick.core :as tick])
  (:import [java.time ZoneOffset ZonedDateTime]
           [java.time.temporal ChronoUnit]))

(defn- pstats-day-key [now]
  (str (tick/date (tick/in now (tick/zone "UTC")))))

(defn profile! [{:biff.admin/keys [pstats]} id f]
  (if (and pstats id)
    (let [[result pstats-data]
          (tufte/profiled {} (tufte/p id (f)))]
      (when pstats-data
        (let [day-key (pstats-day-key (tick/now))]
          (swap! pstats update day-key
                 (fn [existing]
                   (if existing
                     (tufte/merge-pstats existing pstats-data)
                     pstats-data)))))
      result)
    (f)))

(defn- stored-pstats? [value]
  (and (map? value)
       (not (record? value))
       (map? (:stats value))
       (map? (:clock value))))

(defn- pstats->stored-value [pstats]
  (into {} @pstats))

(defn- get-stored-pstats
  [{:keys [biff.core/kv-get biff.core/kv-set] :as ctx} day-key]
  (when kv-get
    (let [value (kv-get ctx :biff.admin/pstats day-key)]
      (cond
        (nil? value)
        nil

        (stored-pstats? value)
        value

        :else
        (do
          (when kv-set
            (kv-set ctx :biff.admin/pstats day-key nil))
          nil)))))

(defn- recent-day-keys [now]
  (let [today     (tick/date (tick/in now (tick/zone "UTC")))
        start-day (tick/<< today (tick/new-period 6 :days))]
    (->> start-day
         (iterate #(tick/>> % (tick/new-period 1 :days)))
         (take 7)
         (mapv str))))

(defn- current-pstats-data [pstats day-keys]
  (let [day-set (set day-keys)]
    (reduce-kv (fn [acc day day-pstats]
                 (if (and (contains? day-set day) day-pstats)
                   (assoc acc day (pstats->stored-value day-pstats))
                   acc))
               {}
               @pstats)))

(defn flush-pstats! [{:keys [biff.admin/pstats biff.core/kv-set] :as ctx}]
  (when (and pstats kv-set)
    (let [current-day       (pstats-day-key (tick/now))
          [all-pstats _new] (swap-vals! pstats #(select-keys % [current-day]))]
      (doseq [[day day-pstats] all-pstats
              :when            day-pstats]
        (kv-set ctx :biff.admin/pstats day
                (pstats->stored-value day-pstats))))))

(defn hourly-schedule-from [now]
  (let [start (-> now
                  (.truncatedTo ChronoUnit/HOURS)
                  (.plusHours 1))]
    (iterate #(.plusHours ^ZonedDateTime % 1) start)))

(defn hourly-schedule []
  (hourly-schedule-from (ZonedDateTime/now ZoneOffset/UTC)))

(defn recent-pstats-data [{:keys [biff.admin/pstats] :as ctx}]
  (let [day-keys  (recent-day-keys (tick/now))
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

(defn get-route-id [ctx]
  (let [method     (some-> (:request-method ctx) name str/upper-case)
        match      (:reitit.core/match ctx)
        route-name (some-> match :data :name)
        route-path (some-> match :template)]
    (when (or route-name route-path)
      (str method " " (or route-name route-path)))))

(defn wrap-profiling [handler]
  (fn [ctx]
    (profile! ctx (get-route-id ctx) #(handler ctx))))

(defn wrap-resolver-profiling
  [resolver]
  (let [id         (:biff.graph/id resolver)
        resolve-fn (:biff.graph/resolve-fn resolver)]
    (assoc resolver :biff.graph/resolve-fn
           (fn [ctx]
             (profile! ctx (str id) #(resolve-fn ctx))))))

(defn- merge-summary-stats [stats]
  (->> stats
       (map stats/summary-stats)
       (reduce stats/summary-stats-merge)))

(defn- merge-recent-pstats [pstats-by-day]
  (let [stats-by-id (reduce (fn [acc pstats]
                              (reduce-kv
                               (fn [acc id stats]
                                 (update acc id conj stats))
                               acc
                               (:stats pstats)))
                            {}
                            (vals pstats-by-day))]
    {:clock {:total (reduce + (map #(get-in % [:clock :total])
                                   (vals pstats-by-day)))}
     :stats (update-vals stats-by-id merge-summary-stats)}))

(defn dashboard-section [ctx]
  (let [pstats-formatted (some-> (recent-pstats-data ctx)
                                 merge-recent-pstats
                                 tufte/format-pstats)]
    (ui/section "Performance Metrics"
                (if pstats-formatted
                  [:pre.bg-gray-100.p-4.rounded.text-xs.overflow-x-auto
                   (str pstats-formatted)]
                  [:p.text-gray-500 "No performance data collected yet."]))))

(defn page [ctx]
  (ui/dashboard-page "performance" (dashboard-section ctx)))

(def routes
  ["/_biff/admin" {:middleware [util/wrap-admin-access]}
   ["/performance" {:get page}]])
