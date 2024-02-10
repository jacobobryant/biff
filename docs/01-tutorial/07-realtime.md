---
title: Realtime updates
---

[View the code for this section](https://github.com/jacobobryant/eelchat/commit/c5e9090a925b65bb99ae9cbc31e75b3fe7b777ce).

In this section, we'll use websockets to deliver new messages to other users
who are in the same channel. htmx has some websocket features that make this
fairly painless.

We'll soon add a handler for websocket connection requests. It will store all
the active websocket connections in an atom, as a map of the form
`channel ID -> set of connections`. The `com.eelchat/initial-system`
map already includes an atom (because the starter app included a
websocket example, and we never removed the atom when we started working on
eelchat), but it contains a set. So let's change it to a map:

```diff
;; src/com/eelchat.clj
;; ...
 (def initial-system
   {:biff/modules #'modules
    :biff/send-email #'email/send-email
    :biff/handler #'handler
    :biff/malli-opts #'malli-opts
    :biff.beholder/on-save #'on-save
    :biff.xtdb/tx-fns biff/tx-fns
-   :com.eelchat/chat-clients (atom #{})})
+   :com.eelchat/chat-clients (atom {})})
```

That atom will be available in all our request maps, under the
`:com.eelchat/chat-clients` key. For changes to `initial-system` to take
effect, you'll need to restart the system. Go to the `dev/repl.clj`
file and evaluate the `(main/refresh)` form. (Alternatively, you can
hit Ctrl-C in the terminal and then run `clj -M:dev dev` again, but that would be
slower.)

Now we can add that websocket connection handler. We'll have htmx start the
connection whenever you enter a channel, and we'll throw in a couple `prn`s for
illustration.

```diff
;; src/com/eelchat/app.clj
;; ...
 (defn channel-page [{:keys [biff/db community channel] :as ctx}]
   (let [messages (q db
                 '{:find (pull message [*])
                   :in [channel]
                   :where [[message :message/channel channel]]}
-                (:xt/id channel))]
+                (:xt/id channel))
+        href (str "/community/" (:xt/id community)
+                  "/channel/" (:xt/id channel))]
     (ui/app-page
      ctx
       [:.border.border-neutral-600.p-3.bg-white.grow.flex-1.overflow-y-auto#messages
-       {:_ "on load or newMessage set my scrollTop to my scrollHeight"}
+       {:hx-ext "ws"
+        :ws-connect (str href "/connect")
+        :_ "on load or newMessage set my scrollTop to my scrollHeight"}
        (map message-view (sort-by :message/created-at messages))]
       [:.h-3]
       (biff/form
-       {:hx-post (str "/community/" (:xt/id community)
-                      "/channel/" (:xt/id channel))
+       {:hx-post href
         :hx-target "#messages"
         :hx-swap "beforeend"
         :_ (str "on htmx:afterRequest"
                " set <textarea/>'s value to ''"
                " then send newMessage to #messages")
         :class "flex"}
        [:textarea.w-full#text {:name "text"}]
        [:.w-2]
        [:button.btn {:type "submit"} "Send"]))))

+(defn connect [{:keys [com.eelchat/chat-clients] {channel-id :xt/id} :channel :as ctx}]
+  {:status 101
+   :headers {"upgrade" "websocket"
+             "connection" "upgrade"}
+   :ws {:on-connect (fn [ws]
+                      (prn :connect (swap! chat-clients update channel-id (fnil conj #{}) ws)))
+        :on-close (fn [ws status-code reason]
+                    (prn :disconnect
+                         (swap! chat-clients
+                                (fn [chat-clients]
+                                  (let [chat-clients (update chat-clients channel-id disj ws)]
+                                    (cond-> chat-clients
+                                      (empty? (get chat-clients channel-id)) (dissoc channel-id)))))))}})
+
;; ...
 (def module
   {:routes ["" {:middleware [mid/wrap-signed-in]}
             ["/app"           {:get app}]
             ["/community"     {:post new-community}]
             ["/community/:id" {:middleware [wrap-community]}
              [""      {:get community}]
              ["/join" {:post join-community}]
              ["/channel" {:post new-channel}]
              ["/channel/:channel-id" {:middleware [wrap-channel]}
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
%%:connect {#uuid "6598..." #{#object[...]}}%%
%%:disconnect {}%%
3ms 101 get  /community/36906af5-9e1b-43ae-a9bf-854d77b14396/channel/84589a45-a56f-4ddb-81d0-aefea5b5a8c7/connect
%%:connect {#uuid "8458..." #{#object[...]}}%%
```

(Feel free to remove those `prn` calls once you've verified the `connect`
handler is working—but leave the `swap!` calls in!)

With the connections in place, we can add a transaction listener that will
send new messages to all the channel participants:

```diff
;; src/com/eelchat/app.clj
(ns com.eelchat.app
   (:require [com.biffweb :as biff :refer [q]]
             [com.eelchat.middleware :as mid]
             [com.eelchat.ui :as ui]
+            [ring.adapter.jetty9 :as jetty]
+            [rum.core :as rum]
             [xtdb.api :as xt]))
;; ...
+(defn on-new-message [{:keys [biff.xtdb/node com.eelchat/chat-clients]} tx]
+  (let [db-before (xt/db node {::xt/tx-id (dec (::xt/tx-id tx))})]
+    (doseq [[op & args] (::xt/tx-ops tx)
+            :when (= op ::xt/put)
+            :let [[doc] args]
+            :when (and (contains? doc :message/text)
+                       (nil? (xt/entity db-before (:xt/id doc))))
+            :let [html (rum/render-static-markup
+                        [:div#messages {:hx-swap-oob "beforeend"}
+                         (message-view doc)
+                         [:div {:_ "init send newMessage to #messages then remove me"}]])]
+            ws (get @chat-clients (:message/channel doc))]
+      (jetty/send! ws html))))
+
 (defn wrap-community [handler]
   (fn [{:keys [biff/db user path-params] :as ctx}]
     (if-some [community (xt/entity db (parse-uuid (:id path-params)))]
;; ...
 (def module
   {:routes ["" {:middleware [mid/wrap-signed-in]}
             ["/app"           {:get app}]
             ["/community"     {:post new-community}]
             ["/community/:id" {:middleware [wrap-community]}
              [""      {:get community}]
              ["/join" {:post join-community}]
              ["/channel" {:post new-channel}]
              ["/channel/:channel-id" {:middleware [wrap-channel]}
               ["" {:get channel-page
                    :post new-message
                    :delete delete-channel}]
-              ["/connect" {:get connect}]]]]})
+              ["/connect" {:get connect}]]]]
+   :on-tx on-new-message})
```

Whenever a new transaction is indexed by XTDB, it will get passed to
`on-new-message`. That function will check to see if the transaction contains a
new message, and if so, the function will send the message via websocket to
anyone who is in the relevant channel. By using a transaction listener, we
ensure that the chat room will work even if you scale out beyond a single web
server: each web server will index the transaction and will send the message to
any channel participants that have a websocket connection to that server.

Since the `on-new-message` function will also handle rendering and sending the
new message to the author, we can remove the rendering bit from the
`new-message` function and replace it with an empty response. Otherwise,
whenever you sent a message, it would look like you sent it twice.

```diff
 (defn new-message [{:keys [channel membership params] :as ctx}]
   (let [message {:xt/id (random-uuid)
              :message/membership (:xt/id membership)
              :message/channel (:xt/id channel)
              :message/created-at (java.util.Date.)
              :message/text (:text params)}]
     (biff/submit-tx (assoc ctx :biff.xtdb/retry false)
       (concat [(assoc message :db/doc-type :message)]
               (command-tx ctx)))
-    (message-view message)))
+    [:<>]))
```

Try it out!

![Screen recording of two chat windows side by side](/img/tutorial/chat-demo.gif)
