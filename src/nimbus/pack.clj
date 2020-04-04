(ns ^:nimbus nimbus.pack
  (:require
    [clj-http.client :as http]
    [clojure.string :as str]
    [nimbus.core :as core]
    [nimbus.util :as util :refer [defmemo]]
    [rum.core :as rum :refer [defc]]
    [trident.util :as u])
  (:import [java.lang.management ManagementFactory]))

(defmemo get-latest-sha (* 1000 60)
  [{:keys [repo-name branch]}]
  (->>
    (http/get (str  "https://api.github.com/repos/" repo-name "/git/refs/heads/" branch)
      {:as :json})
    :body
    :object
    :sha))

(defn norm-repo [{:keys [html_url description default_branch full_name stargazers_count]}]
  {:url html_url
   :description description
   :branch default_branch
   :repo-name full_name
   :stars stargazers_count})

(defmemo all-packages (* 1000 60)
  []
  (->>
    (http/get "https://api.github.com/search/repositories"
      {:query-params {:q "topic:clj-nimbus"}
       :as :json
       :headers {"Accept" "application/vnd.github.mercy-preview+json"}})
    :body
    :items
    (map norm-repo)))

(defmemo get-repo (* 1000 60)
  [repo-name]
  (-> (http/get (str "https://api.github.com/repos/" repo-name)
        {:as :json})
    :body
    norm-repo))

(defn assoc-latest-sha [repo]
  (assoc repo :latest-sha (get-latest-sha repo)))

(defn installed-packages []
  (let [repo-name->url (->> core/config
                         vals
                         (map (juxt ::repo ::app-url))
                         (into {}))
        assoc-url #(assoc % :app-url (repo-name->url (:repo-name %)))]
  (->> (util/deps)
    :deps
    vals
    (filter ::user-package)
    (map (fn [{:keys [git/url] :as package}]
           (-> url
             (str/replace #"^https://github.com/" "")
             get-repo
             (merge package)
             assoc-latest-sha
             assoc-url))))))

(defn available-packages []
  (let [installed-urls (->> (installed-packages)
                         (map :url)
                         set)]
    (->> (all-packages)
      (remove (comp installed-urls :url)))))

(defn need-restart? []
  (> (-> (util/deps)
       :nimbus/config
       (::last-update #inst "1970")
       .getTime)
    (.getStartTime (ManagementFactory/getRuntimeMXBean))))

(defc table [{:keys [title]} contents]
  (when (not-empty contents)
    (list
      [:h5 title]
      [:table.table.table-striped
       [:tbody
        (for [row contents]
          [:tr
           (for [col row]
             [:td {:style {:vertical-align "middle"}}
              col])])]])))

(defc hidden [k v]
  [:input {:name k :value v :type "hidden"}])

(defc installed-packages-table []
  (table {:title "Installed packages"}
    (for [{:keys [sha latest-sha url description branch repo-name stars app-url]}
          (sort-by :repo-name (installed-packages))]
      [[:a {:href url :target "_blank"} repo-name]
       [:div description]
       [:.d-flex
        (when app-url
          [:a.btn.btn-primary.mr-2.btn-sm {:href app-url} "Open"])
        [:form.mb-0 {:method "post"}
         (util/csrf)
         (hidden "action" "uninstall")
         (hidden "repo-name" repo-name)
         [:button.btn.btn-secondary.btn-sm {:type "submit"} "Uninstall"]]
        (when (not= sha latest-sha)
          [:form.mb-0.ml-2 {:method "post"}
           (util/csrf)
           (hidden "action" "update")
           (hidden "repo-name" repo-name)
           (hidden "latest-sha" latest-sha)
           [:button.btn.btn-primary.btn-sm {:type "submit"} "Update"]])]])))

(defc available-packages-table []
  (table {:title "Available packages"}
    (for [{:keys [url description branch repo-name stars]}
          (sort-by :stars (available-packages))]
      [[:a {:href url :target "_blank"} repo-name]
       [:div description]
       [:form.mb-0 {:method "post"}
        (util/csrf)
        (hidden "action" "install")
        (hidden "repo-name" repo-name)
        (hidden "branch" branch)
        [:button.btn.btn-primary {:type "submit"} "Install"]]])))

(defc pack-page [req]
  [:html util/html-opts
   (util/head "Nimbus Pack")
   [:body util/body-opts
    (util/navbar
      [:a.text-secondary {:href "/nimbus/auth/change-password"}
       "Change password"]
      [:.mr-3]
      [:form.form-inline.mb-0 {:method "post" :action "/nimbus/auth/logout"}
       (util/csrf)
       [:button.btn.btn-outline-secondary.btn-sm
        (util/unsafe {:type "submit"} "Sign&nbsp;out")]])
    [:.container-fluid.mt-3
     (when (need-restart?)
       [:.mb-3
        [:div "You must restart Nimbus for changes to take effect."]
        [:form.mb-0 {:method "post" :action "/nimbus/pack/restart"}
         (util/csrf)
         [:button.btn.btn-danger.btn-sm {:type "submit"} "Restart now"]]])
     (installed-packages-table)
     (available-packages-table)]]])

(defn update-pkgs! [f & args]
  (apply util/update-deps!
    (comp #(assoc-in % [:nimbus/config ::last-update] (java.util.Date.)) f)
    args))

(defn handle-action [{{:keys [action repo-name branch latest-sha] :as params} :params}]
  (let [pkg-name (symbol (str "github-" repo-name))]
    (case action
      "install" (update-pkgs! assoc-in [:deps pkg-name]
                  {:git/url (str "https://github.com/" repo-name)
                   :sha (get-latest-sha params)
                   ::user-package true})
      "uninstall" (update-pkgs! update :deps dissoc pkg-name)
      "update" (update-pkgs! assoc-in [:deps pkg-name :sha] latest-sha))))

(defc restart-page [_]
  [:html util/html-opts
   (util/head {:title "Restarting Nimbus"}
     [:script {:src "/nimbus/pack/js/restart.js"}])
   [:body util/body-opts
    (util/navbar)
    [:.container-fluid.mt-3
     [:.d-flex.flex-column.align-items-center
      [:.spinner-border.text-primary {:role "status"}
       [:span.sr-only "Loading..."]]
      [:p.mt-3 "Waiting for Nimbus to restart. If nothing happens within 90 seconds, try "
       [:a {:href "/" :target "_blank"} "opening Nimbus manually"] "."]]]]])

(defn restart-nimbus [_]
  (future
    (Thread/sleep 500)
    (shutdown-agents)
    (System/exit 0))
  (util/render restart-page nil))

(defn ping [_]
  {:status 200
   :body ""
   :headers {"Content-Type" "text/plain"}})

(def config
  {:nimbus.http/home "/nimbus/pack"
   :nimbus.http/route
   ["/nimbus/pack"
    ["/ping" {:get ping
              :name ::ping}]
    ["" {:middleware [util/wrap-authorize]}
     ["" {:get #(util/render pack-page %)
          :post #(util/render pack-page (doto % handle-action))
          :name ::pack}]
     ["/restart" {:post restart-nimbus
                  :name ::restart}]]]})
