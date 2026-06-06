(ns com.example.app.landing-page)

(defn landing-page [{:keys [com.example/app-name] :as _request}]
  {:status  200
   :headers {"content-type" "text/html"}
   :body    (str "<html><body><p>Welcome to "
                 app-name
                 "</p></body></html>")})
(def module
  {:com.example/routes {[:get "/"] landing-page}})
