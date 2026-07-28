(ns com.biffweb.ring.impl.route
  (:require [com.biffweb.fx :as fx]
            [lambdaisland.hiccup :as hiccup]))

(def all-methods
  [:get :post :put :delete :head :options :trace :patch :connect])

(defn wrap-fx-result [f]
  (fn [ctx]
    (f ctx (:biff.ring/fx-result ctx))))

(defn wrap-methods [params wrapper-fn]
  (reduce (fn [m method]
            (if (contains? m method)
              (update m method wrapper-fn)
              m))
          params
          all-methods))

(defn- hiccup-node? [node]
  (and (vector? node)
       (keyword? (first node))))

(defn- hiccup-response [response body]
  (merge {:status 200
          :headers {"content-type" "text/html; charset=utf-8"}}
         response
         {:body (hiccup/render body)}))

(defn wrap-hiccup [f]
  (fn [& args]
    (let [result (apply f args)]
      (cond
        (hiccup-node? result)
        (hiccup-response {} result)

        (and (map? result) (hiccup-node? (:body result)))
        (hiccup-response result (:body result))

        :else
        result))))

(defn prepare-state-fns [initial-fx & {:as state-fns}]
  (cond-> (update-vals state-fns wrap-hiccup)
    initial-fx
    (wrap-methods wrap-fx-result)

    true
    (merge {:start (fn [{:keys [request-method]}]
                     (cond-> {:biff.fx/next request-method}
                       initial-fx
                       (assoc :biff.ring/fx-result initial-fx)))})))

(defn route [uri route-name route-methods handler]
  [uri (into {:name route-name}
             (map (fn [method]
                    [method handler]))
             route-methods)])

(defn validate-uri [uri]
  (assert (or (not uri) (re-matches #"[a-zA-Z0-9-_.+!*/:]+" uri))
          (str "Invalid characters in URL: " uri)))

(defmacro defroute [sym & args]
  (let [default-uri (str "/_biff/api/" *ns* "/" sym)
        route-name  (keyword (str *ns*) (str sym))
        impl-sym    (symbol (str sym "-impl"))]
    `(let [args#          [~@args]
           [uri# & args#] (if (not (string? (first args#)))
                            (into [~default-uri] args#)
                            args#)

           [initial-fx# & kvs#]
           (if (vector? (first args#))
             args#
             (into [nil] args#))

           [~'& {:as state-fns#}] kvs#
           state-fns#             (prepare-state-fns initial-fx# state-fns#)]
       (validate-uri uri#)
       [(def ~impl-sym
          (fx/machine ~route-name state-fns#))
        (def ~sym
          (route uri#
                 ~route-name
                 (filterv state-fns# all-methods)
                 (fn [req#] (#'~impl-sym req#))))])))
