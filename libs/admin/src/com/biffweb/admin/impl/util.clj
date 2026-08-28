(ns com.biffweb.admin.impl.util
  (:require [clojure.string :as str]
            [com.biffweb.admin.impl.ui :as ui]))

(defn wrap-admin-access [handler]
  (fn [{:biff.admin/keys [admin-user-id] :keys [session] :as ctx}]
    (let [current-uid (str (:uid session))
          admin-uid   (str admin-user-id)]
      (cond
        (str/blank? current-uid)
        {:status  401
         :headers {"content-type" "text/html"}
         :body    "<h1>Unauthorized</h1>"}

        (str/blank? admin-uid)
        (ui/admin-setup-page current-uid)

        (not= current-uid admin-uid)
        {:status  403
         :headers {"content-type" "text/html"}
         :body    "<h1>Forbidden</h1>"}

        :else
        (handler ctx)))))
