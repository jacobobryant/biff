(ns com.example.lib.config)

(defn use-config [ctx]
  (merge ctx
         {:com.example/port (parse-long (or (System/getenv "PORT") "8080"))}))
