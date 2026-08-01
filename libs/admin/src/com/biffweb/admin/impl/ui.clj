(ns com.biffweb.admin.impl.ui
  "UI helpers for the admin dashboard."
  (:require [dev.onionpancakes.chassis.core :as chassis]))

(defn admin-page
  "Render an admin page with consistent layout."
  [title & body]
  {:status  200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body
   (str
    "<!DOCTYPE html>\n"
    (chassis/html
     [:html {:lang "en"}
      [:head
       [:meta {:charset "utf-8"}]
       [:meta {:name    "viewport"
               :content "width=device-width, initial-scale=1"}]
       [:title title]
       [:link {:rel "icon" :href "data:,"}]
       [:script {:src "https://cdn.tailwindcss.com"}]]
      [:body.font-sans.bg-gray-50.text-gray-800.p-6.max-w-6xl.mx-auto
       body]]))})

(defn admin-fragment
  "Render an HTML fragment for the admin dashboard content."
  [hiccup]
  {:status  200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (chassis/html hiccup)})

(defn heading [text]
  [:h1.text-2xl.font-bold.mb-6 text])

(defn section [title & body]
  [:div.mb-8
   [:h2.text-xl.font-semibold.mb-4 title]
   body])

(defn- format-bytes
  "Format bytes as human-readable string."
  [bytes]
  (cond
    (>= bytes (* 1024 1024 1024)) (format "%.1f GB" (/ bytes (* 1024.0 1024 1024)))
    (>= bytes (* 1024 1024)) (format "%.1f MB" (/ bytes (* 1024.0 1024)))
    (>= bytes 1024) (format "%.1f KB" (/ bytes 1024.0))
    :else (str bytes " B")))

(defn metrics-table
  "Render a table of daily metrics."
  [days dau wau daily-signups daily-revenue]
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
          [:td.text-right.p-2.border-b (format "$%.2f" (double (get daily-revenue day 0)))])])]]])

(defn resource-usage-table
  "Render resource usage information."
  [{:keys [ram-used ram-total ram-pct disk-used disk-total disk-pct cpu-load cpu-count]}]
  [:div.grid.grid-cols-1.md:grid-cols-3.gap-4
   [:div.bg-white.p-4.rounded.shadow
    [:h3.text-sm.font-semibold.text-gray-500.mb-2 "RAM"]
    [:div.text-2xl.font-bold (format "%.1f%%" (double ram-pct))]
    [:div.text-sm.text-gray-500 (str (format-bytes ram-used) " / " (format-bytes ram-total))]]
   [:div.bg-white.p-4.rounded.shadow
    [:h3.text-sm.font-semibold.text-gray-500.mb-2 "Disk"]
    [:div.text-2xl.font-bold (format "%.1f%%" (double disk-pct))]
    [:div.text-sm.text-gray-500 (str (format-bytes disk-used) " / " (format-bytes disk-total))]]
   [:div.bg-white.p-4.rounded.shadow
    [:h3.text-sm.font-semibold.text-gray-500.mb-2 "CPU"]
    [:div.text-2xl.font-bold (if (neg? cpu-load) "N/A" (format "%.2f" (double cpu-load)))]
    [:div.text-sm.text-gray-500 (str cpu-count " cores")]]])

(defn users-table
  "Render a table of users with impersonation support."
  [users anti-forgery-token]
  [:div
   [:p.text-sm.text-gray-600.mb-2 (str (count users) " users")]
   [:table.w-full.text-sm
    [:thead
     [:tr
      [:th.text-left.p-2.border-b "Email"]
      [:th.text-left.p-2.border-b "User ID"]
      [:th.text-left.p-2.border-b "Joined"]
      [:th.text-left.p-2.border-b "Actions"]]]
    [:tbody
     (for [{:keys [user-id joined-at email]} (take 50 users)]
       [:tr {:key (str user-id)}
        [:td.p-2.border-b (or email "—")]
        [:td.p-2.border-b.font-mono.text-xs (str user-id)]
        [:td.p-2.border-b (str joined-at)]
        [:td.p-2.border-b
         [:button.bg-indigo-600.text-white.px-2.py-1.rounded.text-xs.cursor-pointer
          {:data-user-id (pr-str user-id)
           :onclick      (str "var uid = this.dataset.userId;"
                              "fetch('/_biff/admin/generate-signin-code', {"
                              "method: 'POST',"
                              "headers: {'Content-Type': 'application/x-www-form-urlencoded'},"
                              "body: 'user-id=' + encodeURIComponent(uid)"
                              (when anti-forgery-token
                                (str " + '&__anti-forgery-token=' + encodeURIComponent('" anti-forgery-token "')"))
                              "}).then(r => r.json()).then(d => {"
                              "navigator.clipboard.writeText(d.url);"
                              "this.textContent='Copied!';"
                              "setTimeout(() => this.textContent='Copy sign-in link', 2000);"
                              "});")}
          "Copy sign-in link"]]])]]])

(defn exceptions-table
  "Render a table of recent exceptions."
  [errors]
  [:div
   [:p.text-sm.text-gray-600.mb-2 (str (count errors) " recent exceptions")]
   [:table.w-full.text-sm
    [:thead
     [:tr
      [:th.text-left.p-2.border-b "Timestamp"]
      [:th.text-left.p-2.border-b "Error Message"]
      [:th.text-left.p-2.border-b ""]]]
    [:tbody
     (for [[idx {:keys [message timestamp]}] (map-indexed vector (reverse errors))]
       (let [real-idx (- (dec (count errors)) idx)]
         [:tr {:key (str real-idx)}
          [:td.p-2.border-b.text-xs.whitespace-nowrap (str timestamp)]
          [:td.p-2.border-b.text-sm (subs (str message) 0 (min 120 (count (str message))))]
          [:td.p-2.border-b
           [:a.text-blue-600.hover:underline.text-xs
            {:href (str "/_biff/admin/stacktrace/" real-idx)}
            "View stack trace"]]]))]]])
