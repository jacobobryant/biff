(defn biff.glue
  (:require [biff.crux :as bcrux]
            [biff.util :as bu]
            [reitit.core :as reitit]))

(defn handle-form-tx [{:keys [biff.reitit/get-router
                              params/tx-info
                              params] :as req}
                      {:keys [coercions]}]
  (let [{:keys [tx
                fields
                redirect
                path-params
                query-params]
         :as tx-info} (edn/read-string tx-info)
        route (reitit/match-by-name (get-router) redirect path-params)
        path (reitit/match->path route query-params)
        redirect-ok (get-in route [:data :biff/redirect])
        tx (postwalk (fn [x]
                       (if-some [field-type (get fields x)]
                         ((get coercions field-type identity)
                          (get params (keyword x)))
                         x))
                     tx)]
    (if-not redirect-ok
      (bu/throw-anom :incorrect "Invalid redirect route name."
                     {:redirect redirect})
      (do
        (bcrux/submit-tx (assoc req :biff.crux/authorize true) tx)
        {:status 302
         :headers/location path}))))
