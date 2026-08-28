(ns com.biffweb.admin.impl.alerts
  (:require [clojure.string :as str]
            [com.biffweb.admin.impl.ui :as ui]
            [com.biffweb.admin.impl.util :as util]
            [taoensso.telemere :as tel]
            [taoensso.telemere.tools-logging :as tel.tl]
            [tick.core :as tick]
            [clojure.tools.logging :as log])
  (:import [java.net InetAddress]))

(defn- hostname []
  (try
    (.getHostName (InetAddress/getLocalHost))
    (catch Exception _ "unknown")))

(defn- stored-errors [{:keys [biff.core/kv-get] :as ctx}]
  (when kv-get
    (try
      (kv-get ctx :biff.admin/errors "errors")
      (catch Exception _ nil))))

(defn- recent-errors [{:biff.admin/keys [alert-state] :as ctx}]
  (let [cutoff        (tick/<< (tick/now) (tick/new-duration 72 :hours))
        local-errors  (or (some-> alert-state deref :errors) [])
        remote-errors (-> (stored-errors ctx)
                          (dissoc (hostname))
                          vals
                          (->> (mapcat identity)))]
    (->> remote-errors
         (into local-errors)
         (filterv #(and (:instant %)
                        (tick/<= cutoff (:instant %))))
         (sort-by :instant)
         vec)))

(defn- handle-error
  [{:biff.admin/keys [send-email alert-state alert-email]
    :as              ctx}
   signal]
  (when (= (:level signal) :error)
    (let [max-errors         20
          rate-limit-seconds (* 60 5)
          now-seconds        (/ (System/nanoTime) (* 1000 1000 1000.0))
          formatted          (try
                               ((tel/format-signal-fn {}) signal)
                               (catch Exception e
                                 (str "Error formatting signal: " e "\n"
                                      (pr-str signal))))
          error-entry        {:message
                              (or (some-> signal :error .getMessage)
                                  "Unknown error")

                              :stack-trace formatted
                              :instant     (tick/now)}]
      (when alert-state
        (swap! alert-state update :errors
               (fn [errors]
                 (vec (take-last max-errors
                                 (conj (or errors []) error-entry))))))
      (when (and send-email alert-email alert-state)
        (let [{:keys [batch]}
              (swap! alert-state
                     (fn [{:keys [pending last-sent-at] :as state}]
                       (let [pending (conj (or pending []) formatted)]
                         (if (< rate-limit-seconds
                                (- now-seconds (or last-sent-at 0)))
                           (assoc state
                                  :batch pending
                                  :pending []
                                  :last-sent-at now-seconds)
                           (assoc state
                                  :batch nil
                                  :pending pending
                                  :last-sent-at (or last-sent-at 0))))))]
          (when (not-empty batch)
            (try
              (let [error-text     (str/join "\n\n---\n\n"
                                             (take-last max-errors batch))
                    preview-length (min 1000 (count error-text))
                    preview        (subs error-text 0 preview-length)]
                (send-email ctx
                            {:to      alert-email
                             :subject "Application error alert"
                             :text    error-text
                             :html    (str "<pre>" preview "</pre>")}))
              (catch Exception e
                (binding [*out* *err*]
                  (println "Failed to send error alert email:"
                           (.getMessage e)))))
            (when-let [kv-set (:biff.core/kv-set ctx)]
              (try
                (kv-set ctx :biff.admin/errors "errors"
                        (assoc (or (stored-errors ctx) {})
                               (hostname)
                               (:errors @alert-state)))
                (catch Exception e
                  (binding [*out* *err*]
                    (println "Failed to store recent errors:"
                             (.getMessage e))))))))))))

(defn use-alerts [ctx]
  (tel.tl/tools-logging->telemere!)
  (let [alert-state (atom {:errors [] :pending [] :last-sent-at 0})
        ctx         (assoc ctx :biff.admin/alert-state alert-state)]
    (tel/add-handler! :biff.admin/alerts
                      (fn [signal] (handle-error ctx signal)))
    (update ctx :biff.core/stop conj
            #(tel/remove-handler! :biff.admin/alerts))))

(defn- exceptions-table [errors]
  [:div
   [:p.text-sm.text-gray-600.mb-2 (str (count errors) " recent exceptions")]
   [:table.w-full.text-sm
    [:thead
     [:tr
      [:th.text-left.p-2.border-b "Timestamp"]
      [:th.text-left.p-2.border-b "Error Message"]
      [:th.text-left.p-2.border-b ""]]]
    [:tbody
     (for [[idx {:keys [instant message]}]
           (map-indexed vector (reverse errors))]
       (let [real-idx (- (dec (count errors)) idx)]
         [:tr {:key (str real-idx)}
          [:td.p-2.border-b.text-xs.whitespace-nowrap (str instant)]
          [:td.p-2.border-b.text-sm
           (subs (str message) 0 (min 120 (count (str message))))]
          [:td.p-2.border-b
           [:a.text-blue-600.hover:underline.text-xs
            {:href (str "/_biff/admin/stacktrace/" real-idx)}
            "View stack trace"]]]))]]])

(defn dashboard-section
  [{:biff.admin/keys [alert-state]
    :keys            [biff.stuff/params]
    :as              ctx}]
  (when alert-state
    (let [errors             (recent-errors ctx)
          anti-forgery-token (:anti-forgery-token ctx)
          alert-sent?        (:alert-sent params)]
      (ui/section "Recent Exceptions"
                  [:div
                   (when alert-sent?
                     [:p.bg-green-50.border.border-green-200.p-3.mb-4.rounded
                      "Alert sent."])
                   [:form {:method "post" :action "/_biff/admin/test-alert"}
                    (when anti-forgery-token
                      [:input {:type  "hidden"
                               :name  "__anti-forgery-token"
                               :value anti-forgery-token}])
                    [:button {:class '[bg-red-600 text-white px-3 py-1 rounded
                                       text-sm cursor-pointer mb-4]
                              :type  "submit"}
                     "Test alert"]]
                   (if (seq errors)
                     (exceptions-table errors)
                     [:p.text-gray-500 "No exceptions recorded."])]))))

(defn page [ctx]
  (ui/dashboard-page "errors" (dashboard-section ctx)))

(defn- stacktrace-page-handler
  [{:keys [path-params] :as ctx}]
  (let [index       (try
                      (Integer/parseInt
                       (or (:index path-params) "0"))
                      (catch Exception _ 0))
        errors      (recent-errors ctx)
        error       (get (vec errors) index)
        copy-script (str "navigator.clipboard.writeText("
                         "document.getElementById('stacktrace')"
                         ".textContent);"
                         "this.textContent='Copied!';"
                         "setTimeout(()=>this.textContent="
                         "'Copy to clipboard',2000)")]
    (if error
      (ui/admin-page "Stack Trace"
                     [:div
                      (ui/heading "Stack Trace")
                      [:p.text-sm.text-gray-600.mb-2
                       (str "Error at " (:instant error))]
                      [:p.font-semibold.mb-4 (:message error)]
                      [:button {:class   '[bg-blue-600 text-white px-4 py-2
                                           rounded mb-4 cursor-pointer]
                                :onclick copy-script}
                       "Copy to clipboard"]
                      [:pre#stacktrace
                       {:class '[bg-gray-100 p-4 rounded text-xs overflow-x-auto
                                 whitespace-pre-wrap]}
                       (:stack-trace error)]])
      {:status 404 :headers {"content-type" "text/plain"} :body "Not found"})))

(defn- test-alert-handler [_ctx]
  (log/error (ex-info "Test alert from admin dashboard"
                      {:type :test-alert}))
  {:status  303
   :headers {"location" "/_biff/admin/errors?alert-sent=true"}})

(def routes
  ["/_biff/admin" {:middleware [util/wrap-admin-access]}
   ["/errors" {:get page}]
   ["/stacktrace/:index"
    {:get stacktrace-page-handler}]
   ["/test-alert"
    {:post test-alert-handler}]])
