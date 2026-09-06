(ns com.biffweb.stuff
  (:require [clojure.walk :as walk]))

(defn- keywordize-map [m]
  (into {}
        (keep (fn [[k v]]
                (try
                  [(keyword (name k)) v]
                  (catch Exception _))))
        m))

(defn- normalize-params [request]
  (->> ((juxt :query-params
              :form-params
              :json-params
              :body-params
              :body
              :params)
        request)
       (filter map?)
       (map #(walk/postwalk (fn [x]
                              (if (map? x)
                                (keywordize-map x)
                                x))
                            %))
       (apply merge {})))

(defn wrap-params [handler]
  (fn [request]
    (handler (assoc request :biff.stuff/params (normalize-params request)))))
