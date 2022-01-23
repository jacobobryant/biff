(ns com.example.feat.misc)

(defn echo [req]
  {:status 200
   :body (select-keys req [:params :body-params])})

(def features
  {:routes [["/api/echo" {:get echo
                          :post echo}]]})
