---
title: Messages
---

[View the code for this section](https://github.com/jacobobryant/eelchat/commit/e3a17d6552eb8fc6dfba296edc70ed992a7d4558).

It's finally time to add the core feature of any discussion app: sending and
receiving messages. We're going to update the `com.eelchat.ui/channel-page`
handler so it displays all the messages for the current channel. We'll also add
a text box where you can enter a new message.

Let's use the REPL to add some messages to our database first so that we'll
have some data to start out with. Add the following function to `com.eelchat.repl`:

```clojure
;; src/com/eelchat/repl.clj
;; ...
(defn seed-channels []
  (let [{:keys [biff/db] :as sys} (get-sys)]
    (biff/submit-tx sys
      (for [[mem chan] (q db
                          '{:find [mem chan]
                            :where [[mem :mem/comm comm]
                                    [chan :chan/comm comm]]})]
        {:db/doc-type :message
         :msg/mem mem
         :msg/channel chan
         :msg/created-at :db/now
         :msg/text (str "Seed message " (rand-int 1000))}))))
```

This will create one message for each user for each channel they're in. Add
`(seed-channels)` somewhere inside the `comment` form in that file, then
evaluate it. To make sure it worked, you can evaluate this query (also in the
same file):

```clojure
;; src/com/eelchat/repl.clj
;; ...
  (let [{:keys [biff/db] :as sys} (get-sys)]
    (q db
       '{:find (pull msg [*])
         :where [[msg :msg/text]]}))
```

Now we can render the messages in `com.eelchat.feat.app/channel-page`:

```clojure
;; src/com/eelchat/feat/app.clj
;; ...
(defn message-view [{:msg/keys [mem text created-at]}]
  (let [username (str "User " (subs (str mem) 0 4))]
    [:div
     [:.text-sm
      [:span.font-bold username]
      [:span.w-2.inline-block]
      [:span.text-gray-600 (biff/format-date created-at "d MMM h:mm aa")]]
     [:p.whitespace-pre-wrap.mb-6 text]]))

(defn channel-page [{:keys [biff/db community channel] :as req}]
  (let [msgs (q db
                '{:find (pull msg [*])
                  :in [channel]
                  :where [[msg :msg/channel channel]]}
                (:xt/id channel))]
    (ui/app-page
     req
     [:.border.border-neutral-600.p-3.bg-white.grow.flex-1.overflow-y-auto#messages
      (map message-view (sort-by :msg/created-at msgs))])))
```

![A screenshot of the app, with messages rendered by channel-page](/img/tutorial/render-messages.png)

Next step is to add the text box for sending messages. To make things a bit more convenient,
let's update our middleware first. We'll make `wrap-community` add a `:mem` key to the request, set
to the current user's membership document. We'll need that for the `:msg/mem` key when we create new
messages.

We'll also tighten the `wrap-channel` middleware so it only gives access to users who have joined
the community:

```diff
;; src/com/eelchat/feat/app.clj
;; ...
 (defn wrap-community [handler]
   (fn [{:keys [biff/db user path-params] :as req}]
     (if-some [community (xt/entity db (parse-uuid (:id path-params)))]
-      (let [roles (->> (:user/mems user)
-                       (filter (fn [mem]
-                                 (= (:xt/id community) (get-in mem [:mem/comm :xt/id]))))
-                       first
-                       :mem/roles)]
-        (handler (assoc req :community community :roles roles)))
+      (let [mem (->> (:user/mems user)
+                     (filter (fn [mem]
+                               (= (:xt/id community) (get-in mem [:mem/comm :xt/id]))))
+                     first)
+            roles (:mem/roles mem)]
+        (handler (assoc req :community community :roles roles :mem mem)))
       {:status 303
        :headers {"location" "/app"}})))
 
 (defn wrap-channel [handler]
-  (fn [{:keys [biff/db user community path-params] :as req}]
+  (fn [{:keys [biff/db user mem community path-params] :as req}]
     (let [channel (xt/entity db (parse-uuid (:chan-id path-params)))]
-      (if (= (:chan/comm channel) (:xt/id community))
+      (if (and (= (:chan/comm channel) (:xt/id community)) mem)
         (handler (assoc req :channel channel))
         {:status 303
          :headers {"Location" (str "/community/" (:xt/id community))}}))))
```

Now we can add the new message text box. We'll use htmx to insert new messages
into the page without doing a full page reload, and we'll use hyperscript to
keep the message window scrolled to the bottom whenever there's a new message:

```diff
;; src/com/eelchat/feat/app.clj
;; ...
       [:span.text-gray-600 (biff/format-date created-at "d MMM h:mm aa")]]
      [:p.whitespace-pre-wrap.mb-6 text]]))
 
+(defn new-message [{:keys [channel mem params] :as req}]
+  (let [msg {:xt/id (random-uuid)
+             :msg/mem (:xt/id mem)
+             :msg/channel (:xt/id channel)
+             :msg/created-at (java.util.Date.)
+             :msg/text (:text params)}]
+    (biff/submit-tx (assoc req :biff.xtdb/retry false)
+      [(assoc msg :db/doc-type :message)])
+    (message-view msg)))
+
 (defn channel-page [{:keys [biff/db community channel] :as req}]
   (let [msgs (q db
                 '{:find (pull msg [*])
;; ...
                 (:xt/id channel))]
     (ui/app-page
      req
-     [:.border.border-neutral-600.p-3.bg-white.grow.flex-1.overflow-y-auto#messages
-      (map message-view (sort-by :msg/created-at msgs))])))
+      [:.border.border-neutral-600.p-3.bg-white.grow.flex-1.overflow-y-auto#messages
+       {:_ "on load or newMessage set my scrollTop to my scrollHeight"}
+       (map message-view (sort-by :msg/created-at msgs))]
+      [:.h-3]
+      (biff/form
+       {:hx-post (str "/community/" (:xt/id community)
+                      "/channel/" (:xt/id channel))
+        :hx-target "#messages"
+        :hx-swap "beforeend"
+        :_ (str "on htmx:afterRequest"
+                " set <textarea/>'s value to ''"
+                " then send newMessage to #messages")
+        :class "flex"}
+       [:textarea.w-full#text {:name "text"}]
+       [:.w-2]
+       [:button.btn {:type "submit"} "Send"]))))
 
;; ...
              ["/channel" {:post new-channel}]
              ["/channel/:chan-id" {:middleware [wrap-channel]}
               ["" {:get channel-page
+                   :post new-message
                    :delete delete-channel}]]]]})
```

We also used a trick in `new-message` to speed up the response time: We set the
`:biff.xtdb/retry` option to false. Normally, `biff/submit-tx` will block until
the submitted transaction is indexed, both so you can "read your
writes," and so that the transaction can be retried if there was contention
(for example, if there were two transactions trying to update the same document
at the same time).

In this case, we know there won't be any contention since we're creating a
brand new document, so disabling retries means we can send the new message to
the client immediately after we submit the transaction.

Anyway. Try it out:

![Screenshot of the channel with several messages in it and the new message text box](/img/tutorial/new-message.png)

If you sign in as a second user, you should be able to have both users send
messages to the same channel. However, you won't see messages from the other
user unless you do a page refresh. We'll fix that in the next section.

But one last thing before we move on: we need to update our `delete-channel`
function so that it deletes the channel's messages too.

```clojure
;; src/com/eelchat/feat/app.clj
;; ...
(defn delete-channel [{:keys [biff/db channel roles] :as req}]
  (when (contains? roles :admin)
    (biff/submit-tx req
      (for [id (conj (q db
                        '{:find msg
                          :in [channel]
                          :where [[msg :msg/channel channel]]}
                        (:xt/id channel))
                     (:xt/id channel))]
        {:db/op :delete
         :xt/id id})))
  [:<>])
```
