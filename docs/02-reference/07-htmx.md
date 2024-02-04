---
title: htmx
---

[htmx](https://htmx.org/) allows us to create interactive user interfaces
declaratively with HTML instead of using JavaScript (or ClojureScript). It
works by returning snippets of HTML from the server in response to user
actions. For example, the following code will cause the button to be replaced
with some text after it's clicked:

```clojure
(defn page [request]
  [:html
   [:head
    [:script {:src "https://unpkg.com/htmx.org@1.9.0"}]]
   ...
   [:form {:hx-post "/click" :hx-swap "outerHTML"}
    [:button {:type "submit"} "Don't click this button"]
    ...]])

(defn click [request]
  [:div "Earth will now self-destruct"])

(def module
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
   [:head
    [:script {:src "https://unpkg.com/htmx.org@1.9.0"}]
    [:script {:src "https://unpkg.com/htmx.org/dist/ext/ws.js"}]]
   ...
   [:div {:hx-ext "ws" :ws-connect "/app/chat"}
    [:div#messages]
    [:form {:ws-send true}
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

(def module
  {:routes [["/chat-page" {:get chat-page}]
            ["/chat-ws" {:get chat-ws}]]})
```

(Note that this chat room will only work if all the participants are connected
to the same web server. For that reason it's better to call `jetty/send!` from
a [transaction listener](/docs/reference/transaction-listeners/).)

## Beyond htmx

While you can use htmx for things like in-page navigation (such as switching
between different tabs), this will cause some unnecessary lag since it involves
a trip to the server. When you need to do a bit of lightweight client-side
scripting, htmx's companion library [hyperscript](https://hyperscript.org/) can
come in handy.

For example, you can use hyperscript for tab-switching:

```clojure
(def tabs ["foo" "bar" "baz"])

(defn tabs-component [ctx]
  [:<>
   [:.flex
    (for [id tabs]
      [:label
       [:input {:type "radio"
                :name "tab"
                :value id
                :checked (when (= id "foo")
                           true)
                ;; Hyperscript goes in the :_ attribute.
                :_ "on change show .section in #tab-sections when its id is my value"}]
       id])]
   [:div#tab-sections
    (for [id tabs]
      [:.section {:id id
                  :style (when-not (= id "foo")
                           {:display "none"})}
       "This is the " id " section"])]])
```

hyperscript is convenient for one-liners like the above, but when it starts
to feel awkward, you can always use some plain JS. If needed, create a file
at `resources/public/js/main.js` and include it in your pages:

```clojure
(defn page [request]
  [:html
   [:head
    [:script {:src "/js/main.js"}]]
   ...])
```

Finally, if you start writing a bunch of unwieldy UI-related JS, you can take a look at
[Alpine.js](https://alpinejs.dev/). Alpine is a lightweight framework that lets
you layer reactivity on top of your server-rendered HTML, so it fits nicely with htmx.

See also:

 - [htmx documentation](https://htmx.org/docs/)
 - [hyperscript documentation](https://hyperscript.org/docs/)
 - [Alpine documentation](https://alpinejs.dev/)
