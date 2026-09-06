(ns com.biffweb.admin.impl.resource
  (:require [com.biffweb.admin.impl.ui :as ui]
            [com.biffweb.admin.impl.util :as util])
  (:import [java.io File]
           [java.lang.management ManagementFactory]))

(defn- get-resource-usage []
  (let [runtime    (Runtime/getRuntime)
        total-mem  (.totalMemory runtime)
        free-mem   (.freeMemory runtime)
        max-mem    (.maxMemory runtime)
        used-mem   (- total-mem free-mem)
        filesystem (File. (System/getProperty "user.dir" "."))
        disk-total (.getTotalSpace filesystem)
        disk-free  (.getUsableSpace filesystem)
        disk-used  (- disk-total disk-free)
        load-avg   (try
                     (.getSystemLoadAverage
                      (ManagementFactory/getOperatingSystemMXBean))
                     (catch Exception _ -1.0))
        cpu-count  (.availableProcessors runtime)]
    {:ram-used   used-mem
     :ram-total  max-mem
     :ram-pct    (if (pos? max-mem) (* 100.0 (/ used-mem (double max-mem))) 0)
     :disk-used  disk-used
     :disk-total disk-total
     :disk-pct   (if (pos? disk-total)
                   (* 100.0 (/ disk-used (double disk-total))) 0)
     :cpu-load   load-avg
     :cpu-count  cpu-count}))

(defn- format-bytes [bytes]
  (cond
    (>= bytes (* 1024 1024 1024))
    (format "%.1f GB" (/ bytes (* 1024.0 1024 1024)))

    (>= bytes (* 1024 1024)) (format "%.1f MB" (/ bytes (* 1024.0 1024)))
    (>= bytes 1024) (format "%.1f KB" (/ bytes 1024.0))
    :else (str bytes " B")))

(defn- resource-usage-table
  [{:keys [ram-used ram-total ram-pct disk-used disk-total disk-pct
           cpu-load cpu-count]}]
  [:div.grid.grid-cols-1.md:grid-cols-3.gap-4
   [:div.bg-white.p-4.rounded.shadow
    [:h3.text-sm.font-semibold.text-gray-500.mb-2 "RAM"]
    [:div.text-2xl.font-bold (format "%.1f%%" (double ram-pct))]
    [:div.text-sm.text-gray-500
     (str (format-bytes ram-used) " / " (format-bytes ram-total))]]
   [:div.bg-white.p-4.rounded.shadow
    [:h3.text-sm.font-semibold.text-gray-500.mb-2 "Disk"]
    [:div.text-2xl.font-bold (format "%.1f%%" (double disk-pct))]
    [:div.text-sm.text-gray-500
     (str (format-bytes disk-used) " / " (format-bytes disk-total))]]
   [:div.bg-white.p-4.rounded.shadow
    [:h3.text-sm.font-semibold.text-gray-500.mb-2 "System Load"]
    [:div.text-2xl.font-bold
     (if (neg? cpu-load) "N/A" (format "%.2f" (double cpu-load)))]
    [:div.text-sm.text-gray-500 (str "across " cpu-count " cores")]]])

(defn dashboard-section []
  (ui/section "Resource Usage"
              (resource-usage-table (get-resource-usage))))

(defn page [_ctx]
  (ui/dashboard-page "system" (dashboard-section)))

(def routes
  ["/_biff/admin" {:middleware [util/wrap-admin-access]}
   ["/system" {:get page}]])
