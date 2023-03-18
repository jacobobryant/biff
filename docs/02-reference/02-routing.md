---
title: Routing
---

Biff uses [Ring](https://github.com/ring-clojure/ring) and
[Reitit](https://github.com/metosin/reitit) for handling HTTP requests. Reitit
has a lot of features, but you can go far with just a few basics.

Multiple routes:

```clojure
(defn foo [request]
  {:status 200
   :headers {"content-type" "text/plain"}
   :body "foo response"})

(defn bar ...)

(def features
  {:routes [["/foo" {:get foo}]
            ["/bar" {:post bar}]]})
```

Path parameters:

```clojure
(defn click [{:keys [path-params] :as request}]
  (println (:token path-params))
  ...)

(def features
  {:routes [["/click/:token" {:get click}]]})
```

Nested routes:

```clojure
(def features
  {:routes [["/auth/"
             ["send" {:post send-token}]
             ["verify/:token" {:get verify-token}]]]})
```

With middleware:

```clojure
(defn wrap-signed-in [handler]
  (fn [{:keys [session] :as req}]
    (if (some? (:uid session))
      (handler req)
      {:status 303
       :headers {"location" "/"}})))

(def features
  {:routes [["/app" {:middleware [wrap-signed-in]}
             ["" {:get app}]
             ["/set-foo" {:post set-foo}]]]})
```

If you need to provide a public API, you can use `:api-routes` to disable
CSRF protection (this is a Biff feature, not a Reitit one):

```clojure
(defn echo [{:keys [params]}]
  {:status 200
   :headers {"content-type" "application/json"}
   :body params})

(def features
  {:api-routes [["/echo" {:post echo}]]})
```

Biff includes some middleware (`wrap-render-rum`) which will treat vector responses
as Rum. The following handlers are equivalent:

```clojure
(require '[rum.core :as rum])

(defn my-handler [request]
  {:status 200
   :headers {"content-type" "text/html"}
   :body (rum/render-static-markup
           [:html
            [:body
             [:p "I'll gladly pay you Tuesday for a hamburger on Tuesday"]]])})

(defn my-handler [request]
  [:html
   [:body
    [:p "I'll gladly pay you Tuesday for a hamburger on Tuesday"]]])
```

See also:

 - [Reitit documentation](https://cljdoc.org/d/metosin/reitit/CURRENT/doc/introduction)
 - [`reitit-handler`](https://github.com/jacobobryant/biff/blob/bdd1bd81d95ee36c615495a946c7c1aa92d19e2e/src/com/biffweb.clj#L84)
