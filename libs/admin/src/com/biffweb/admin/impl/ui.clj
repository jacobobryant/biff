(ns com.biffweb.admin.impl.ui
  (:require [dev.onionpancakes.chassis.core :as chassis]
            [ring.util.response :as ring.response]))

(defn admin-page [title & body]
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
       [:link {:rel "stylesheet" :href "/_biff/admin/main.css"}]]
      [:body.font-sans.bg-gray-50.text-gray-800.p-6
       body]]))})

(defn heading [text]
  [:h1.text-2xl.font-bold.mb-6 text])

(defn tabs [selected]
  [:nav.flex.flex-wrap.gap-2.mb-8.border-b
   (for [[tab label href] [["metrics" "Metrics" "/_biff/admin"]
                           ["users" "Users" "/_biff/admin/users"]
                           ["performance" "Performance"
                            "/_biff/admin/performance"]
                           ["errors" "Errors" "/_biff/admin/errors"]
                           ["system" "System" "/_biff/admin/system"]]]
     [:a.px-4.py-2.-mb-px.border-b-2
      {:class (if (= selected tab)
                ["border-blue-600" "text-blue-700" "font-semibold"]
                ["border-transparent" "text-gray-600" "hover:text-gray-900"])
       :href  href}
      label])])

(defn dashboard-page [selected content]
  (admin-page "Admin"
              [:div
               (heading "Admin")
               (tabs selected)
               content]))

(defn section [title & body]
  [:div.mb-8
   [:h2.text-xl.font-semibold.mb-4 title]
   body])

(defn admin-setup-page [current-uid]
  (let [copy-script (str "navigator.clipboard.writeText('"
                         current-uid "');"
                         "this.textContent='Copied!';"
                         "setTimeout(()=>this.textContent='Copy',2000)")]
    (admin-page "Admin Setup"
                [:div
                 (heading "Admin Setup")
                 [:p.mb-4 (str ":biff.admin/user-id is not set. "
                               "Your current user ID is:")]
                 [:div.flex.items-center.gap-2.mb-4
                  [:code.bg-gray-100.p-2.rounded.text-sm.break-all
                   {:id "uid-display"} current-uid]
                  [:button {:class   '[bg-blue-600 text-white px-3 py-1 rounded
                                       text-sm cursor-pointer]
                            :onclick copy-script}
                   "Copy"]]
                 [:p.text-sm.text-gray-600
                  (str "Set :biff.admin/user-id to enable the admin "
                       "dashboard.")]])))

(defn stylesheet-handler [_ctx]
  (ring.response/resource-response "com/biffweb/admin/main.css"))

(def routes
  ["/_biff/admin/main.css" {:get stylesheet-handler}])
