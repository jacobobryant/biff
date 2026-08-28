(ns com.biffweb.admin.impl.metrics
  (:require [com.biffweb.admin.impl.ui :as ui]
            [com.biffweb.admin.impl.util :as util]
            [tick.core :as tick]))

(defn- day-key [instant tz]
  (tick/date (tick/in instant tz)))

(defn- date-range [start-date end-date]
  (->> (iterate #(tick/>> % (tick/new-period 1 :days)) start-date)
       (take-while #(tick/<= % end-date))
       vec))

(defn compute-dau [events tz now]
  (let [today  (tick/date (tick/in now tz))
        cutoff (tick/<< today (tick/new-period 30 :days))]
    (->> events
         (filterv :instant)
         (filterv #(tick/<= cutoff (day-key (:instant %) tz)))
         (group-by #(day-key (:instant %) tz))
         (reduce-kv (fn [m day evts]
                      (assoc m day (count (set (map :user-id evts)))))
                    (sorted-map)))))

(defn compute-wau [events tz now]
  (let [today         (tick/date (tick/in now tz))
        cutoff        (tick/<< today (tick/new-period 37 :days))
        events        (->> events
                           (filterv :instant)
                           (filter #(tick/<= cutoff (day-key (:instant %) tz))))
        events-by-day (group-by #(day-key (:instant %) tz) events)
        end-date      today
        start-date    (tick/<< today (tick/new-period 29 :days))
        days          (date-range start-date end-date)]
    (into (sorted-map)
          (map (fn [day]
                 (let [window-start (tick/<< day (tick/new-period 6 :days))
                       window-days  (date-range window-start day)
                       unique-users (->> window-days
                                         (mapcat #(get events-by-day %))
                                         (keep :user-id)
                                         set
                                         count)]
                   [day unique-users])))
          days)))

(defn compute-daily-signups [users tz now]
  (let [today  (tick/date (tick/in now tz))
        cutoff (tick/<< today (tick/new-period 30 :days))]
    (->> users
         (filterv :joined-at)
         (filterv #(tick/<= cutoff (day-key (:joined-at %) tz)))
         (group-by #(day-key (:joined-at %) tz))
         (reduce-kv (fn [m day u]
                      (assoc m day (count u)))
                    (sorted-map)))))

(defn compute-daily-revenue [events tz now]
  (let [today  (tick/date (tick/in now tz))
        cutoff (tick/<< today (tick/new-period 30 :days))]
    (->> events
         (filterv :instant)
         (filter #(tick/<= cutoff (day-key (:instant %) tz)))
         (group-by #(day-key (:instant %) tz))
         (reduce-kv (fn [m day evts]
                      (assoc m day (reduce + 0 (map :revenue evts))))
                    (sorted-map)))))

(defn- metrics-table [days dau wau daily-signups daily-revenue]
  [:div.overflow-x-auto
   [:table.w-full.text-sm
    [:thead
     [:tr
      [:th.text-left.p-2.border-b "Date"]
      [:th.text-right.p-2.border-b "DAU"]
      [:th.text-right.p-2.border-b "WAU"]
      [:th.text-right.p-2.border-b "Signups"]
      (when daily-revenue [:th.text-right.p-2.border-b "Revenue"])]]
    [:tbody
     (for [day (reverse days)]
       [:tr {:key (str day)}
        [:td.p-2.border-b (str day)]
        [:td.text-right.p-2.border-b (get dau day 0)]
        [:td.text-right.p-2.border-b (get wau day 0)]
        [:td.text-right.p-2.border-b (get daily-signups day 0)]
        (when daily-revenue
          [:td.text-right.p-2.border-b
           (format "$%.2f" (double (get daily-revenue day 0)))])])]]])

(defn dashboard-section
  [{:biff.admin/keys [get-user-events get-revenue-events] :as ctx}
   timezone users]
  (let [tz             (try (tick/zone timezone)
                            (catch Exception _ (tick/zone "UTC")))
        now            (tick/now)
        user-events    (when get-user-events (get-user-events ctx))
        revenue-events (when get-revenue-events (get-revenue-events ctx))
        dau            (compute-dau (or user-events []) tz now)
        wau            (compute-wau (or user-events []) tz now)
        daily-signups  (when users (compute-daily-signups users tz now))
        daily-revenue  (when revenue-events
                         (compute-daily-revenue revenue-events tz now))
        recent-days    (->> (keys dau) (take-last 30))]
    (ui/section "Usage Metrics"
                (when (seq recent-days)
                  (metrics-table recent-days dau wau
                                 daily-signups daily-revenue))
                (when-not (seq recent-days)
                  [:p.text-gray-500 "No activity data available."]))))

(defn page
  [{:biff.admin/keys [get-users] :as ctx}]
  (let [{:keys [timezone]
         :or   {timezone "UTC"}} (:biff.stuff/params ctx)
        user-data                (when get-users (get-users ctx))]
    (ui/dashboard-page
     "metrics"
     (dashboard-section ctx timezone user-data))))

(def routes
  ["/_biff/admin" {:middleware [util/wrap-admin-access]}
   ["" {:get page}]])
