(ns com.biffweb.ring.impl
  (:require [lambdaisland.hiccup :as hiccup]))

(def ^:private all-methods
  [:get :post :put :delete :head :options :trace :patch :connect])

(defn- safe-for-url? [s]
  (boolean (re-matches #"[a-zA-Z0-9-_.+!*]+" s)))

(defn autogen-endpoint [ns* sym]
  (let [href (str "/_biff/api/" ns* "/" sym)]
    (doseq [segment [ns* sym]]
      (assert (safe-for-url? (str segment))
              (str "URL segment would contain invalid characters: " segment)))
    href))

(defn wrap-result [f]
  (fn [ctx]
    (f ctx (:biff.fx/result ctx))))

(defn wrap-methods [params wrapper-fn]
  (reduce (fn [m method]
            (if (contains? m method)
              (update m method wrapper-fn)
              m))
          params
          all-methods))

(defn route*
  [uri route-name machine-fn & {:as state->transition-fn}]
  (let [machine* (machine-fn route-name state->transition-fn)]
    [uri
     (into {:name route-name}
           (comp (filter state->transition-fn)
                 (map (fn [method]
                        [method machine*])))
           all-methods)]))

(defn- hiccup-node? [node]
  (and (vector? node)
       (keyword? (first node))))

(defn- render-hiccup-response [response body]
  (-> response
      (assoc :status (or (:status response) 200)
             :body (hiccup/render body))
      (update :headers #(merge {"content-type" "text/html; charset=utf-8"} (or % {})))))

(defn wrap-hiccup [f]
  (fn [& args]
    (let [result (apply f args)]
      (cond
        (hiccup-node? result)
        (render-hiccup-response {} result)

        (and (map? result) (hiccup-node? (:body result)))
        (render-hiccup-response result (:body result))

        :else
        result))))
