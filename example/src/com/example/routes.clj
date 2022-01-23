(ns com.example.routes)

(defn echo [req]
  {:status 200
   :body (select-keys req [:params :body-params])})

(def routes
  [["/api/echo" {:get echo
                 :post echo}]])
