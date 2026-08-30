(ns com.biffweb.admin.impl.users
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [com.biffweb.admin.impl.ui :as ui]
            [com.biffweb.admin.impl.util :as util]
            [com.biffweb.fx :refer [defpipeline]]
            [tick.core :as tick])
  (:import [java.security SecureRandom]))

(defn- generate-secure-code [n-bytes]
  (let [sr (SecureRandom.)
        bs (byte-array n-bytes)]
    (.nextBytes sr bs)
    (str/replace
     (.encodeToString (java.util.Base64/getUrlEncoder) bs)
     #"=" "")))

(defn- base-url-from-request [ctx]
  (let [scheme (or (some-> ctx :headers (get "x-forwarded-proto"))
                   (name (or (:scheme ctx) :http)))
        host   (or (some-> ctx :headers (get "x-forwarded-host"))
                   (get-in ctx [:headers "host"])
                   "localhost")]
    (str scheme "://" host)))

(defn- url-encode [value]
  (java.net.URLEncoder/encode (str value) "UTF-8"))

(defpipeline generate-signin-code-handler
  (fn [{:keys [biff.fx/now biff.stuff/params] :as ctx}]
    (let [user-id-str (:user-id params)
          code        (generate-secure-code 32)
          base        (base-url-from-request ctx)]
      {:stored [:biff.core/kv-set :biff.admin/signin-code code
                {:user-id      (edn/read-string user-id-str)
                 :generated-at now}]

       :biff.fx/return
       {:status 303

        :headers
        {"location"
         (str "/_biff/admin/users?signin-url="
              (url-encode (str base "/_biff/admin/signin/" code)))}}})))

(defpipeline signin-handler
  (fn [{:keys [path-params]}]
    [:biff.core/kv-get :biff.admin/signin-code (:code path-params)])

  (fn [{:keys [biff.fx/now path-params session]} entry]
    (let [valid? (and entry
                      (tick/< (tick/between
                               (tick/instant (:generated-at entry)) now)
                              (tick/new-duration 5 :minutes)))]
      (if valid?
        {:deleted [:biff.core/kv-set
                   :biff.admin/signin-code (:code path-params) nil]

         :biff.fx/return
         {:status  303
          :headers {"location" "/"}
          :session (assoc session :uid (:user-id entry))}}
        {:status  401
         :headers {"content-type" "text/plain"}
         :body    "Unauthorized"}))))

(defn- parse-page [value]
  (try
    (max 1 (Integer/parseInt (or value "1")))
    (catch Exception _ 1)))

(defn- search-users [users search]
  (let [search (some-> search str/trim str/lower-case)]
    (if (str/blank? search)
      users
      (filterv (fn [{:keys [email user-id]}]
                 (str/includes? (str/lower-case (str email " " user-id))
                                search))
               users))))

(defn- page-href [page search]
  (str "/_biff/admin/users?user-page=" page
       (when-not (str/blank? search)
         (str "&user-search=" (url-encode search)))))

(defn- users-table [users anti-forgery-token page search]
  (let [page-size   50
        users       (vec (search-users users search))
        total-users (count users)
        total-pages (max 1 (int (Math/ceil (/ total-users (double page-size)))))
        page        (min page total-pages)
        page-users  (->> users
                         (drop (* (dec page) page-size))
                         (take page-size))]
    [:div
     [:form.flex.gap-2.mb-4 {:method "get" :action "/_biff/admin/users"}
      [:input.border.rounded.px-3.py-1
       {:type        "search"       :name "user-search" :value search
        :placeholder "Search users"}]
      [:button.bg-gray-200.rounded.px-3.py-1 {:type "submit"} "Search"]]
     [:p.text-sm.text-gray-600.mb-2 (str total-users " users")]
     [:table.w-full.text-sm
      [:thead
       [:tr
        [:th.text-left.p-2.border-b "Email"]
        [:th.text-left.p-2.border-b "User ID"]
        [:th.text-left.p-2.border-b "Joined"]
        [:th.text-left.p-2.border-b "Actions"]]]
      [:tbody
       (for [{:keys [user-id joined-at email]} page-users]
         [:tr {:key (str user-id)}
          [:td.p-2.border-b (or email "—")]
          [:td.p-2.border-b.font-mono.text-xs (str user-id)]
          [:td.p-2.border-b (str joined-at)]
          [:td.p-2.border-b
           [:form {:method "post"
                   :action "/_biff/admin/generate-signin-code"}
            [:input {:type "hidden" :name "user-id" :value (pr-str user-id)}]
            (when anti-forgery-token
              [:input {:type  "hidden"
                       :name  "__anti-forgery-token"
                       :value anti-forgery-token}])
            [:button {:class '[bg-indigo-600 text-white px-2 py-1 rounded
                               text-xs cursor-pointer]
                      :type  "submit"}
             "Copy sign-in link"]]]])]]
     [:div.flex.items-center.gap-3.mt-4.text-sm
      (when (< 1 page)
        [:a.text-blue-600.hover:underline
         {:href (page-href (dec page) search)} "Previous"])
      [:span (str "Page " page " of " total-pages)]
      (when (< page total-pages)
        [:a.text-blue-600.hover:underline
         {:href (page-href (inc page) search)} "Next"])]]))

(defn- copy-signin-link [signin-url]
  [[:div.bg-indigo-50.border.border-indigo-200.p-3.mb-4.rounded
    {:data-clipboard         signin-url
     :data-clipboard-on-load true}
    "Sign-in link copied to clipboard."]])

(defn dashboard-section
  [{:keys [biff.stuff/params]} users anti-forgery-token signin-url]
  (let [{:keys [user-page user-search]} params]
    (ui/section "Users"
                (when signin-url
                  (copy-signin-link signin-url))
                (if (seq users)
                  (users-table users anti-forgery-token
                               (parse-page user-page) user-search)
                  [:p.text-gray-500 "No user data available."]))))

(defn page
  [{:biff.admin/keys [get-users] :as ctx}]
  (let [user-data  (when get-users (get-users ctx))
        signin-url (:signin-url (:biff.stuff/params ctx))]
    (ui/dashboard-page
     "users"
     (dashboard-section ctx user-data
                        (:anti-forgery-token ctx) signin-url))))

(def routes
  [""
   ["/_biff/admin/signin/:code" {:get signin-handler}]
   ["/_biff/admin" {:middleware [util/wrap-admin-access]}
    ["/users" {:get page}]
    ["/generate-signin-code" {:post generate-signin-code-handler}]]])
