(ns ^:nimbus nimbus.pack
  (:require
    [clojure.string :as str]
    [clojure.edn :as edn]
    [clojure.core.memoize :as memo]
    [trident.util :as u]
    [ring.middleware.anti-forgery :as anti-forgery]
    [taoensso.timbre :as timbre :refer [trace debug info warn error tracef debugf infof warnf errorf]]
    [nimbus.comms :refer [api-send api]]
    [clj-http.client :as http]
    [ring.util.response :as resp]
    [rum.core :as rum :refer [defc]])
  (:import [java.lang.management ManagementFactory]))

(def subscriptions (atom #{}))

(defmethod api ::subscribe
  [{:keys [uid admin] :as event} _]
  (when admin
    (swap! subscriptions conj uid)
    (api-send uid [::subscribe
                   {:query nil
                    :changeset {[::deps nil]
                                (edn/read-string (slurp "deps.edn"))}}]))
  nil)

(defmethod api ::fire
  [{:keys [admin] :as event} _]
  (u/pprint event)
  (println admin))

(defc csrf []
  [:input#__anti-forgery-token
   {:name "__anti-forgery-token"
    :type "hidden"
    :value (force anti-forgery/*anti-forgery-token*)}])

(defn unsafe [html]
  {:dangerouslySetInnerHTML {:__html html}})

; curl -H "Accept: application/vnd.github.mercy-preview+json"
; https://api.github.com/search/repositories?q=topic:clj-nimbus | python -m json.tool

(defn get-latest-sha* [{:keys [repo-name branch]}]
  ;curl https://api.github.com/repos/izuzak/pmrpc/git/refs/heads/master
  (->>
    (http/get (str  "https://api.github.com/repos/" repo-name "/git/refs/heads/" branch)
      {:as :json})
    :body
    :object
    :sha))
(def get-latest-sha (memo/ttl get-latest-sha* :ttl/threshold (* 1000 60)))

(defn norm-repo [{:keys [html_url description default_branch full_name stargazers_count]}]
  {:url html_url
   :description description
   :branch default_branch
   :repo-name full_name
   :stars stargazers_count})

(defn all-packages* []
  (->>
    (http/get "https://api.github.com/search/repositories"
      {:query-params {:q "topic:clj-nimbus"}
       :as :json
       :headers {"Accept" "application/vnd.github.mercy-preview+json"}})
    :body
    :items
    (map norm-repo)))
(def all-packages (memo/ttl all-packages* :ttl/threshold (* 1000 60)))

(defn deps []
  (-> "deps.edn"
    slurp
    edn/read-string))

(defn get-repo* [repo-name]
  (-> (http/get (str "https://api.github.com/repos/" repo-name)
        {:as :json})
    :body
    norm-repo))
(def get-repo (memo/ttl get-repo* :ttl/threshold (* 1000 60)))

(defn assoc-latest-sha [repo]
  (assoc repo :latest-sha (get-latest-sha repo)))

(defn installed-packages []
  (->> (deps)
    :deps
    vals
    (filter :nimbus/user-package)
    (map (fn [{:keys [git/url] :as package}]
           (-> url
             (str/replace #"^https://github.com/" "")
             get-repo
             (merge package)
             assoc-latest-sha)))))

(defn available-packages []
  (let [installed-urls (->> (installed-packages)
                         (map :url)
                         set)]
    (->> (all-packages)
      (remove (comp installed-urls :url)))))

(defn need-restart? []
  (> (-> (deps)
       :nimbus/config
       (:last-update #inst "1970")
       .getTime)
    (.getStartTime (ManagementFactory/getRuntimeMXBean))))

(defc pack-page [req]
  [:html {:lang "en-US"
          :style {:min-height "100%"}}
   [:head
    [:title "Nimbus Pack"]
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:link
     {:crossorigin "anonymous"
      :integrity "sha384-ggOyR0iXCbMQv3Xipma34MD+dH/1fQ784/j6cY/iJTQUOhcWr7x9JvoRxT2MZw1T"
      :href "https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css"
      :rel "stylesheet"}]]
   [:body {:style {:font-family "'Helvetica Neue', Helvetica, Arial, sans-serif"}}
    [:nav.navbar.navbar-light.bg-light.justify-content-between.align-items-center
     [:.navbar-brand "Nimbus"]
     [:form.form-inline.mb-0 {:method "post" :action "/nimbus/auth/logout"}
      (csrf)
      [:button.btn.btn-outline-secondary.btn-sm (merge {:type "submit"} (unsafe "Sign&nbsp;out"))]]]
    [:.container-fluid.mt-3
     (when (need-restart?)
       [:div.mb-3
        [:div "You must restart Nimbus for changes to take effect."]
        [:form.mb-0 {:method "post" :action "/nimbus/pack/restart"}
         (csrf)
         [:button.btn.btn-danger.btn-sm {:type "submit"} "Restart now"]]])
     (when-some [packages (not-empty (installed-packages))]
       (list
         [:h5 "Installed packages"]
         [:table.table.table-striped
          [:tbody
           (for [{:keys [sha latest-sha url description branch repo-name stars]}
                 (sort-by :repo-name packages)]
             [:tr
              [:td {:style {:vertical-align "middle"}}
               [:a {:href url :target "_blank"} repo-name]]
              [:td {:style {:vertical-align "middle"}}
               [:div description]]
              [:td {:style {:vertical-align "middle"}}
               [:.d-flex
                [:form.mb-0 {:method "post"}
                 (csrf)
                 [:input {:name "action" :value "uninstall" :type "hidden"}]
                 [:input {:name "repo-name" :value repo-name :type "hidden"}]
                 [:button.btn.btn-secondary {:type "submit"} "Uninstall"]]
                (when (not= sha latest-sha)
                  [:form.mb-0.ml-2 {:method "post"}
                   (csrf)
                   [:input {:name "action" :value "update" :type "hidden"}]
                   [:input {:name "repo-name" :value repo-name :type "hidden"}]
                   [:input {:name "latest-sha" :value latest-sha :type "hidden"}]
                   [:button.btn.btn-primary {:type "submit"} "Update"]])]]])]]))
     (when-some [packages (not-empty (available-packages))]
       (list
         [:h5 "Available packages"]
         [:table.table.table-striped
          [:tbody
           (for [{:keys [url description branch repo-name stars]}
                 (sort-by :stars packages)]
             [:tr
              [:td {:style {:vertical-align "middle"}}
               [:a {:href url :target "_blank"} repo-name]]
              [:td {:style {:vertical-align "middle"}}
               [:div description]]
              [:td {:style {:vertical-align "middle"}}
               [:form.mb-0 {:method "post"}
                (csrf)
                [:input {:name "action" :value "install" :type "hidden"}]
                [:input {:name "repo-name" :value repo-name :type "hidden"}]
                [:input {:name "branch" :value branch :type "hidden"}]
                [:button.btn.btn-primary {:type "submit"} "Install"]]]])]]))]]])

(defn render [f]
  (fn [req]
    {:status 200
     :body (rum/render-static-markup (f req))
     :headers {"Content-Type" "text/html"}}))

(defn write-deps! [deps]
  (-> deps
    (assoc-in [:nimbus/config :last-update] (java.util.Date.))
    u/pprint
    with-out-str
    (#(spit "deps.edn" %))))

(defn update-deps! [f & args]
  (write-deps! (apply f (deps) args)))

(defn wrap-action [{{:keys [action repo-name branch latest-sha] :as params} :params :as req}]
  (let [pkg-name (symbol (str "github-" repo-name))]
    (case action
      "install" (update-deps! assoc-in [:deps pkg-name]
                  {:git/url (str "https://github.com/" repo-name)
                   :sha (get-latest-sha params)
                   :nimbus/user-package true})
      "uninstall" (update-deps! update :deps dissoc pkg-name)
      "update" (update-deps! assoc-in [:deps pkg-name :sha] latest-sha)))
  req)

(defn wrap-authorize [handler]
  (fn [req]
    (if (get-in req [:session :admin])
      (handler req)
      {:status 302
       :headers {"Location" "/nimbus/auth?next=/nimbus/pack"}
       :body ""})))

(defn restart-nimbus [_]
  (future
    (Thread/sleep 1000)
    (shutdown-agents)
    (System/exit 0))
  {:status 302
   :headers {"Location" "/nimbus/pack"}
   :body ""})

(def config
  {:nimbus.comms/route
   ["" {:middleware [wrap-authorize]}
    ["/nimbus/pack" {:get (render pack-page)
                     :post (comp (render pack-page) wrap-action)
                     :name ::pack}]
    ["/nimbus/pack/restart" {:post restart-nimbus
                             :name ::restart}]]})
