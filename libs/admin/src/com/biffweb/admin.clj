(ns com.biffweb.admin
  (:require [com.biffweb.admin.impl.alerts :as alerts]
            [com.biffweb.admin.impl.module :as module]
            [com.biffweb.admin.impl.profiling :as profiling]
            [com.biffweb.core :as biff.core]))

(biff.core/register
 {;; internal
  :biff.admin/alert-state [:fn #(instance? clojure.lang.IAtom %)]
  :biff.admin/pstats      [:fn #(instance? clojure.lang.IAtom %)]

  ;; public
  :biff.admin/alert-email        'string?
  :biff.admin/get-revenue-events 'ifn?
  :biff.admin/get-usage-events   'ifn?
  :biff.admin/get-users          'ifn?
  :biff.admin/revenue-event      [:map
                                  [:instant 'inst?]
                                  [:revenue 'number?]]
  :biff.admin/send-email         'ifn?
  :biff.admin/send-email-input   [:map
                                  [:to 'string?]
                                  [:subject 'string?]
                                  [:text 'string?]
                                  [:html 'string?]]
  :biff.admin/user               [:map
                                  [:user-id 'some?]
                                  [:email {:optional true} 'string?]
                                  [:joined-at {:optional true} 'inst?]]
  :biff.admin/usage-event        [:map
                                  [:user-id 'some?]
                                  [:instant 'inst?]]
  :biff.admin/admin-user-id      'some?})

(defn profile!
  "Calls `f`, storing profiling data from Tufte.

   Stores the run time of `f` in the `pstats` atom, keyed by `id`."
  {:arglists '([{:biff.admin/keys [pstats]} id f])}
  [ctx id f]
  (profiling/profile! ctx id f))

(defn wrap-profiling
  "Wraps a Reitit Ring handler with `profile!`.

   Uses the request method and Reitit route name or path template as the
   profiling ID."
  [handler]
  (profiling/wrap-profiling handler))

(defn wrap-resolver-profiling
  "Wraps a biff.graph resolver with `profile!`.

   The resolver's :biff.graph/id is used as the profiling ID."
  [resolver]
  (profiling/wrap-resolver-profiling resolver))

(defn flush-pstats!
  "Persists profiling data with :biff.core/kv-set.

   Moves data from the `pstats` atom into the database via `kv-set`, with
   :biff.admin/pstats as the KV namespace."
  {:arglists '([{:keys [biff.admin/pstats biff.core/kv-set] :as ctx}])}
  [ctx]
  (profiling/flush-pstats! ctx))

(defn routes
  "Returns the admin dashboard's Reitit routes.

   `options` is merged into each request. See docs/schema.md for the available
   options."
  [options]
  (module/routes options))

(defn module
  "Returns a biff.core module for the admin dashboard.

   Includes `routes`, biff.ring and biff.graph profiling middleware, an hourly
   biff.background task that calls `flush-pstats!`, and initialization for
   profiling state."
  [params]
  (module/module params))

(defn use-alerts
  "A biff.core component that sends email alerts for logged errors.

   Forwards clojure.tools.logging errors to Telemere, and adds a Telemere signal
   handler that stores logged errors in memory. Errors are reported via email in
   batches of at most 20. Emails are rate-limited at 1 per 5 minutes. If more
   than 20 errors are logged in that time, only the most recent 20 are reported.

   Errors reported via email are also stored via kv-set and are viewable on the
   biff.admin dashboard."
  {:arglists '([{:biff.admin/keys [send-email alert-email] :as ctx}])}

  [ctx]
  (alerts/use-alerts ctx))
