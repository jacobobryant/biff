---
title: Realtime updates
---

[View the code for this section](https://github.com/jacobobryant/eelchat/commit/1d436ffdb6238f7632fc3e67d35fb995faea528f).

In this section, we'll use websockets to deliver new messages to other users
who are in the same channel. htmx has some websocket features that make this
fairly painless.

Make sure you're on at least version 1.8.4, then install the websocket extension:

```diff
;; src/com/eelchat/ui.clj
;; ...
                      :image "/img/logo.png"})
        (update :base/head (fn [head]
                             (concat [[:link {:rel "stylesheet" :href (css-path)}]
-                                     [:script {:src "https://unpkg.com/htmx.org@1.6.1"}]
+                                     [:script {:src "https://unpkg.com/htmx.org@1.8.4"}]
+                                     [:script {:src "https://unpkg.com/htmx.org@1.8.4/dist/ext/ws.js"}]
                                      [:script {:src "https://unpkg.com/hyperscript.org@0.9.3"}]
                                      [:link {:href "/apple-touch-icon.png", :sizes "180x180", :rel "apple-touch-icon"}]
                                      [:link {:href "/favicon-32x32.png", :sizes "32x32", :type "image/png", :rel "icon"}]
```

We'll soon add a handler for websocket connections. It will store all the
active websocket connections in an atom, as a nested map of the form
`channel ID -> membership ID -> connection`. Our `com.eelchat/start` function
already initializes an atom (because the original example app included a
websocket example, and we never removed the atom when we started working on
eelchat), but it contains a set. So let's change it to a map:

```diff
;; src/com/eelchat.clj
;; ...

 (defn start []
   (biff/start-system
-   {:com.eelchat/chat-clients (atom #{})
+   {:com.eelchat/chat-clients (atom {})
     :biff/features #'features
     :biff/after-refresh `start
     :biff/handler #'handler
```

That atom will be available in all our request maps, under the
`:com.eelchat/chat-clients` key. For changes to the `start` function to take
effect, you'll need to restart the system. Go to `com.eelchat.repl` and
evaluate the `(biff/fix-print (biff/refresh))` form. (Alternatively, you can
hit Ctrl-C in the terminal and then run `bb dev` again, but that would be
slower.)

Now we can add that websocket connection handler. We'll have htmx start the
connection whenever you enter a channel, and we'll throw in a couple `prn`s for
illustration.

```diff
;; src/com/eelchat/feat/app.clj
;; ...
 (defn channel-page [{:keys [biff/db community channel] :as req}]
   (let [msgs (q db
                 '{:find (pull msg [*])
                   :in [channel]
                   :where [[msg :msg/channel channel]]}
-                (:xt/id channel))]
+                (:xt/id channel))
+        href (str "/community/" (:xt/id community)
+                  "/channel/" (:xt/id channel))]
     (ui/app-page
      req
       [:.border.border-neutral-600.p-3.bg-white.grow.flex-1.overflow-y-auto#messages
-       {:_ "on load or newMessage set my scrollTop to my scrollHeight"}
+       {:hx-ext "ws"
+        :ws-connect (str href "/connect")
+        :_ "on load or newMessage set my scrollTop to my scrollHeight"}
        (map message-view (sort-by :msg/created-at msgs))]
       [:.h-3]
       (biff/form
-       {:hx-post (str "/community/" (:xt/id community)
-                      "/channel/" (:xt/id channel))
+       {:hx-post href
         :hx-target "#messages"
         :hx-swap "beforeend"
         :_ (str "on htmx:afterRequest"
;; ...
        [:.w-2]
        [:button.btn {:type "submit"} "Send"]))))
 
+(defn connect [{:keys [com.eelchat/chat-clients]
+                {chan-id :xt/id} :channel
+                {mem-id :xt/id} :mem
+                :as req}]
+  {:status 101
+   :headers {"upgrade" "websocket"
+             "connection" "upgrade"}
+   :ws {:on-connect (fn [ws]
+                      (prn :connect (swap! chat-clients assoc-in [chan-id mem-id] ws)))
+        :on-close (fn [ws status-code reason]
+                    (prn :disconnect
+                         (swap! chat-clients
+                                (fn [chat-clients]
+                                  (let [chat-clients (update chat-clients chan-id dissoc mem-id)]
+                                    (if (empty? (get chat-clients chan-id))
+                                      (dissoc chat-clients chan-id)
+                                      chat-clients))))))}})
+
 (defn wrap-community [handler]
   (fn [{:keys [biff/db user path-params] :as req}]
     (if-some [community (xt/entity db (parse-uuid (:id path-params)))]
;; ...
              ["/channel/:chan-id" {:middleware [wrap-channel]}
               ["" {:get channel-page
                    :post new-message
-                   :delete delete-channel}]]]]})
+                   :delete delete-channel}]
+              ["/connect" {:get connect}]]]]})
```

If you mosey on over to `localhost:8080`, enter one of your community's
channels, and then switch to another channel, you should have some output like
this:

```plaintext
5ms 101 get  /community/36906af5-9e1b-43ae-a9bf-854d77b14396/channel/6598b381-22ac-47d2-9203-7b0ce2997a41/connect
%%:connect {#uuid "6598..." {#uuid "7988..." #object[...]}}%%
%%:disconnect {}%%
3ms 101 get  /community/36906af5-9e1b-43ae-a9bf-854d77b14396/channel/84589a45-a56f-4ddb-81d0-aefea5b5a8c7/connect
%%:connect {#uuid "8458..." {#uuid "7988..." #object[...]}}%%
```

(Feel free to remove those `prn` calls once you've verified the `connect`
handler is working—but leave the `swap!` calls in!)

With the connections in place, we can add a transaction listener that will
send new messages to all the channel participants:

```diff
;; src/com/eelchat/feat/app.clj
;; ...
   (:require [com.biffweb :as biff :refer [q]]
             [com.eelchat.middleware :as mid]
             [com.eelchat.ui :as ui]
+            [ring.adapter.jetty9 :as jetty]
+            [rum.core :as rum]
             [xtdb.api :as xt]))

 (defn app [req]
;; ...
                                  (dissoc chat-clients chan-id)
                                  chat-clients)))))}})

+(defn on-new-message [{:keys [biff.xtdb/node com.eelchat/chat-clients]} tx]
+  (let [db-before (xt/db node {::xt/tx-id (dec (::xt/tx-id tx))})]
+    (doseq [[op & args] (::xt/tx-ops tx)
+            :when (= op ::xt/put)
+            :let [[doc] args]
+            :when (and (contains? doc :msg/text)
+                       (nil? (xt/entity db-before (:xt/id doc))))
+            :let [html (rum/render-static-markup
+                        [:div#messages {:hx-swap-oob "beforeend"}
+                         (message-view doc)
+                         [:div {:_ "init send newMessage to #messages then remove me"}]])]
+            [mem-id client] (get @chat-clients (:msg/channel doc))
+            :when (not= mem-id (:msg/mem doc))]
+      (jetty/send! client html))))
+
 (defn wrap-community [handler]
   (fn [{:keys [biff/db user path-params] :as req}]
     (if-some [community (xt/entity db (parse-uuid (:id path-params)))]
;; ...
 (def features
   {:routes ["" {:middleware [mid/wrap-signed-in]}
;; ...
               ["" {:get channel-page
                    :post new-message
                    :delete delete-channel}]
-              ["/connect" {:get connect}]]]]})
+              ["/connect" {:get connect}]]]]
+   :on-tx on-new-message})
```

Try it out!

![Screen recording of two chat windows side by side](/img/tutorial/chat-demo.gif)
