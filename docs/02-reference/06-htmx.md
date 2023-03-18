---
title: htmx
---

[htmx](https://htmx.org/) allows us to create interactive user interfaces
without JavaScript (or ClojureScript). It works by returning snippets of HTML
from the server in response to user actions. For example, the following code will cause
the button to be replaced with some text after it's clicked:

```clojure
(defn page [request]
  [:html
   [:head
    [:script {:src "https://unpkg.com/htmx.org@1.6.1"}]]
   ...
   [:form {:hx-post "/click" :hx-swap "outerHTML"}
    [:button {:type "submit"} "Don't click this button"]
    ...]])

(defn click [request]
  [:div "What the hell, I told you not to click that!"])

(def features
  {:routes [["/page" {:get page}]
            ["/click" {:post click}]]})
```

(You use htmx by setting `:hx-*` attributes on your HTML elements.)

You can also use htmx to establish websocket connections:

```clojure
(require '[ring.adapter.jetty9 :as jetty])
(require '[rum.core :as rum])

(defn chat-page [request]
  [:html
   ...
   [:div {:hx-ws "connect:/chat-ws"}
    [:div#messages]
    [:form {:hx-ws "send"}
     [:textarea {:name "text"}]
     [:button {:type "submit"} "Send message"]]]])

(defn chat-ws [{:keys [example/chat-clients] :as req}]
  ;; chat-clients is initialized to (atom #{})
  {:status 101
   :headers {"upgrade" "websocket"
             "connection" "upgrade"}
   :ws {:on-connect (fn [ws]
                      (swap! chat-clients conj ws))
        :on-text (fn [ws text]
                   (doseq [ws @chat-clients]
                     (jetty/send! ws (rum/render-static-markup
                                       [:div#messages {:hx-swap-oob "beforeend"}
                                        [:p "new message: " text]]))))
        :on-close (fn [ws status-code reason]
                    (swap! chat-clients disj ws))}})

(def features
  {:routes [["/chat-page" {:get chat-page}]
            ["/chat-ws" {:get chat-ws}]]})
```

(Note that this chat room will only work if all the participants are connected
to the same web server. For that reason it's better to call `jetty/send!` from
a transaction listener&mdash;see the next section.)

You can also use htmx's companion library
[hyperscript](https://hyperscript.org/) to do lightweight frontend scripting.
htmx is good when you need to contact the server anyway; hyperscript is good
when you don't. Our previous button example could be done with hyperscript
instead of htmx:

```clojure
(defn page [request]
  [:html
   [:head
    [:script {:src "https://unpkg.com/hyperscript.org@0.9.3"}]]
   ...
   [:div#message]
   [:button {:_ "on click put 'tsk tsk' into #message then remove me"}
    "Don't click this button"]])
```

See also:

 - [htmx documentation](https://htmx.org/docs/)
 - [Hyperscript documentation](https://hyperscript.org/docs/)
