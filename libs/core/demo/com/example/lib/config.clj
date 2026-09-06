(ns com.example.lib.config)

(def module
  {:biff.core/id :com.example/config

   :biff.core/start
   (fn [ctx]
     (merge ctx
            {:com.example/port
             (parse-long (or (System/getenv "PORT") "8080"))}))})
