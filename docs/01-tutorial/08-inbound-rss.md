---
title: Inbound RSS
---

[View the code for this section](https://github.com/jacobobryant/eelchat/commit/6a0f3eeb84c081c6e29054007ff44ae5d44054ee).

All of eelchat's core functionality is now in place. In this section, we'll add
a nice-to-have: inbound RSS feeds. You can add RSS feeds to channels, and
whenever one of the feeds has a new item, it'll get posted to the channel.
This will give us an excuse to use Biff's
[scheduled tasks](https://biffweb.com/docs/reference/scheduled-tasks/) and
[queues](https://biffweb.com/docs/reference/queues/).

First we'll add a `:subscription` document type to our schema:

```diff
;; src/com/eelchat/schema.clj
;; ...
              [:channel/title     :string]
              [:channel/community :community/id]]

+    :subscription/id :uuid
+    :subscription [:map {:closed true}
+                   [:xt/id                      :subscription/id]
+                   [:subscription/url           :string]
+                   [:subscription/channel       :channel/id]
+                   [:subscription/last-post-uri {:optional true} :string]
+                   [:subscription/fetched-at    {:optional true} inst?]
+                   [:subscription/last-modified {:optional true} :string]
+                   [:subscription/etag          {:optional true} :string]]
+
    :message/id :uuid
    :message [:map {:closed true}
              [:xt/id               :message/id]
```

`:subscription/last-modified` and `:subscription/etag` will hold the values of their respective
response headers whenever we fetch the RSS feed. This will let us avoid
downloading the feed when there haven't been any new items yet.
`:subscription/last-post-uri` will help us double check if we've already seen the feed's
most recent item or not.

Next, the boring part: we need some UI for subscribing to RSS feeds. Let's
take the lazy way out and implement it as a set of chat commands. Community
admins will be able to add subscriptions by typing `/subscribe [url]`. You can
list the subscriptions by typing `/list`, and you can unsubscribe via
`/unsubscribe [url]`. This way we don't have to figure out where to stick another form
in the UI.

Our RSS bot will need to write messages of its own to the channel (both to provide output for
chat commands and to display new RSS items), so let's add `:system` as a possible value for
the `:message/membership` key:

```diff
;; src/com/eelchat/schema.clj
;; ...
    :message/id :uuid
    :message [:map {:closed true}
              [:xt/id              :message/id]
-             [:message/membership :membership/id]
+             [:message/membership [:or :membership/id [:enum :system]]]
              [:message/text       :string]
              [:message/channel    :channel/id]
              [:message/created-at inst?]]})
```

We'll need to update our message rendering code to handle the new value. Let's just get
that out of the way now:

```diff
;; src/com/eelchat/app.clj
;; ...
 (defn message-view [{:message/keys [membership text created-at]}]
-  (let [username (str "User " (subs (str membership) 0 4))]
+  (let [username (if (= :system membership)
+                   "🎅🏻 System 🎅🏻"
+                   (str "User " (subs (str membership) 0 4)))]
     [:div
      [:.text-sm
       [:span.font-bold username]
```

Now we'll modify the `com.eelchat.app/new-message` function to have it
inspect the messages and execute commands when appropriate. Let's add a `command-tx`
function that returns a transaction representing the result of the command (which can
be empty if there was no command to execute):

```diff
;; src/com/eelchat/app.clj
(ns com.eelchat.app
   (:require [com.biffweb :as biff :refer [q]]
             [com.eelchat.middleware :as mid]
             [com.eelchat.ui :as ui]
+            [clojure.string :as str]
             [ring.adapter.jetty9 :as jetty]
             [rum.core :as rum]
             [xtdb.api :as xt]))
;; ...
+(defn command-tx [{:keys [biff/db channel roles params]}]
+  (let [subscribe-url (second (re-find #"^/subscribe ([^\s]+)" (:text params)))
+        unsubscribe-url (second (re-find #"^/unsubscribe ([^\s]+)" (:text params)))
+        list-command (= (str/trimr (:text params)) "/list")
+        message (fn [text]
+                  {:db/doc-type :message
+                   :message/membership :system
+                   :message/channel (:xt/id channel)
+                   :message/text text
+                   ;; Make sure this message comes after the user's message.
+                   :message/created-at (biff/add-seconds (java.util.Date.) 1)})]
+    (cond
+      list-command
+      [(message (apply
+                 str
+                 "Subscriptions:"
+                 (for [url (->> (q db
+                                   '{:find (pull subscription [:subscription/url])
+                                     :in [channel]
+                                     :where [[subscription :subscription/channel channel]]}
+                                   (:xt/id channel))
+                                (map :subscription/url)
+                                sort)]
+                   (str "\n - " url))))]
+
+      (not (contains? roles :admin))
+      nil
+
+      subscribe-url
+      [{:db/doc-type :subscription
+        :db.op/upsert {:subscription/url subscribe-url
+                       :subscription/channel (:xt/id channel)}}
+       (message (str "Subscribed to " subscribe-url))]
+
+      unsubscribe-url
+      [{:db/op :delete
+        :xt/id (biff/lookup-id db :subscription/channel (:xt/id channel) :subscription/url unsubscribe-url)}
+       (message (str "Unsubscribed from " unsubscribe-url))])))
+
 (defn new-message [{:keys [channel membership params] :as ctx}]
   (let [message {:xt/id (random-uuid)
              :message/membership (:xt/id membership)
              :message/channel (:xt/id channel)
              :message/created-at (java.util.Date.)
              :message/text (:text params)}]
     (biff/submit-tx (assoc ctx :biff.xtdb/retry false)
-      [(assoc message :db/doc-type :message)])
+      (concat [(assoc message :db/doc-type :message)]
+              (command-tx ctx)))
     [:<>]))
```

Try it out:

![Screenshot of a channel with the /subscribe, /list, and /unsubscribe commands](/img/tutorial/subscribe.png)

Now that the CRUD is taken care of, we need to actually fetch the RSS feeds and
such. We'll add a scheduled task that runs every 5 minutes and fetches any
feeds which haven't been fetched in the past 30 minutes. If any of those feeds
have a new post, we'll write a message to the appropriate channel with an
excerpt from the post and a link.

Add these dependencies to `deps.edn`:

```diff
;; deps.edn
;; ...
 {:paths ["src" "resources" "target/resources"]
  :deps {com.biffweb/biff                    #:git{:url "https://github.com/jacobobryant/biff", :sha "529660f", :tag "v1.0.0"}
         camel-snake-kebab/camel-snake-kebab {:mvn/version "0.4.3"}
+        remus/remus                         {:mvn/version "0.2.2"}
+        org.jsoup/jsoup                     {:mvn/version "1.11.3"}
         metosin/muuntaja                    {:mvn/version "0.6.8"}
```

(Sometimes dependencies can be added automatically, but in this case you'll
need to hit Ctrl-C and run `clj -M:dev dev` again.)

We'll use Remus to fetch and parse the RSS feeds, and we'll use jsoup to
convert the posts' HTML content to plain text for our excerpts. Create a new
namespace `com.eelchat.subscriptions` with the following contents:

```clojure
;; src/com/eelchat/subscriptions.clj
(ns com.eelchat.subscriptions
  (:require [com.biffweb :as biff :refer [q]]
            [remus :as remus])
  (:import [org.jsoup Jsoup]))

(defn every-n-minutes [n]
  (iterate #(biff/add-seconds % (* n 60)) (java.util.Date.)))

(defn subscriptions-to-update [db]
  (q db
     '{:find (pull subscription [*])
       :in [t]
       :where [[subscription :subscription/url]
               [(get-attr subscription :subscription/fetched-at #inst "1970")
                [fetched-at ...]]
               [(<= fetched-at t)]]}
     (biff/add-seconds (java.util.Date.) (* -60 30))))

(defn assoc-result [{:keys [biff/base-url]} {:subscription/keys [url last-modified etag] :as subscription}]
  (assoc subscription ::result (biff/catchall-verbose
                                (remus/parse-url
                                 url
                                 {:headers (biff/assoc-some
                                            {"User-Agent" base-url}
                                            "If-None-Match" etag
                                            "If-Modified-Since" last-modified)
                                  :socket-timeout 5000
                                  :connection-timeout 5000}))))

(defn format-post [{:keys [title author published-date updated-date link contents]}]
  (let [text-body (some-> contents
                          first
                          :value
                          (Jsoup/parse)
                          (.text))
        text-body (if (and text-body (< 300 (count text-body)))
                    (str (subs text-body 0 300) "...")
                    text-body)]
    (str title " | " author " | " (or published-date updated-date) "\n"
         text-body "\n"
         link)))

(defn subscription-tx [{:subscription/keys [channel last-post-uri] :keys [xt/id ::result]}]
  (let [post (-> result :feed :entries first)
        uri ((some-fn :uri :link) post)]
    (concat [(biff/assoc-some
              {:db/doc-type :subscription
               :db/op :update
               :xt/id id
               :subscription/fetched-at :db/now}
              :subscription/last-post-uri uri
              :subscription/last-modified (get-in result [:response :headers "Last-Modified"])
              :subscription/etag (get-in result [:response :headers "Etag"]))]
            (when (and (some? uri) (not= uri last-post-uri))
              [{:db/doc-type :message
                :message/membership :system
                :message/channel channel
                :message/created-at :db/now
                :message/text (format-post post)}]))))

(defn fetch-rss [{:keys [biff/db] :as ctx}]
  (biff/submit-tx ctx
    (->> (subscriptions-to-update db)
         (map #(assoc-result ctx %))
         (mapcat subscription-tx))))

(def module
  {:tasks [{:task #'fetch-rss
            :schedule #(every-n-minutes 5)}]})
```

Then register the new namespace in your app:

```diff
;; src/com/eelchat.clj
 (ns com.eelchat
   (:require [com.biffweb :as biff]
             [com.eelchat.email :as email]
             [com.eelchat.app :as app]
             [com.eelchat.auth :as auth]
             [com.eelchat.home :as home]
+            [com.eelchat.subscriptions :as sub]
             [com.eelchat.schema :refer [malli-opts]]
             [clojure.java.io :as io]
             [clojure.string :as str]
;; ...
 (def modules
   [app/module
    (biff/authentication-module {})
    home/module
+   sub/module
    schema/module])
```

Scheduled tasks are only started when the system starts, so for our new task to
take effect, you'll need to go to `dev/repl.clj` and evaluate the
`(main/refresh)` form. (Again, you can alternatively hit
Ctrl-C in the terminal and re-run `bb dev`, but it'll be slower. However
sometimes that's a good option if you accidentally bork something and
`main/refresh` doesn't work.)

Once the system starts up, it will run our scheduled task
immediately. If you already added a subscription to one of your channels, you
should see the latest post in a message. (You may need to refresh the page in
case our websocket connection was still being re-established when the message
was sent.)

![Screenshot of a channel containing an RSS post from our scheduled task](/img/tutorial/task-output.png)

If you add a new subscription and want to trigger the scheduled task
immediately without waiting five minutes, you can head over to
`dev/repl.clj` and add
`(com.eelchat.subscriptions/fetch-rss (get-context))`
somewhere within the `comment` form. Evaluate it to fetch the feeds.

That being said, it would be nice if eelchat fetched new subscriptions right
away automatically. Let's add another transaction listener that will do just
that whenever there's a new subscription document:

```diff
;; src/com/eelchat/app.clj
;; ...
 (ns com.eelchat.app
   (:require [com.biffweb :as biff :refer [q]]
+            [com.eelchat.subscriptions :as sub]
             [com.eelchat.middleware :as mid]
             [com.eelchat.ui :as ui]
             [clojure.string :as str]
;; ...

+(defn on-new-subscription [{:keys [biff.xtdb/node] :as ctx} tx]
+  (let [db-before (xt/db node {::xt/tx-id (dec (::xt/tx-id tx))})]
+    (doseq [[op & args] (::xt/tx-ops tx)
+            :when (= op ::xt/put)
+            :let [[doc] args]
+            :when (and (contains? doc :subscription/url)
+                       (nil? (xt/entity db-before (:xt/id doc))))]
+      (future
+       (biff/submit-tx ctx
+         (sub/subscription-tx (sub/assoc-result ctx doc)))))))
+
+(defn on-tx [ctx tx]
+  (on-new-message ctx tx)
+  (on-new-subscription ctx tx))
+
 (defn wrap-community [handler]
   (fn [{:keys [biff/db user path-params] :as ctx}]
     (if-some [community (xt/entity db (parse-uuid (:id path-params)))]
;; ...
                    :post new-message
                    :delete delete-channel}]
               ["/connect" {:get connect}]]]]
-   :on-tx on-new-message})
+   :on-tx on-tx})
```

(We've used a `future` because Biff runs all the transaction listeners
synchronously. We don't want to potentially hold up other transaction listeners
while we're fetching an RSS feed.)

Now if you add a new subscription to one of your channels, the latest post
should show up immediately.

This should work fine for now, but what about when you have a lot of users? We
might end up fetching a lot of different RSS feeds concurrently. That's
not *necessarily* a problem, but it could be if e.g. we add start doing something
computationally intensive as part of our RSS task. We'd like to be able to make sure
we don't have too many background tasks running at once.

This is where Biff's
[in-memory queues](https://biffweb.com/docs/reference/queues/) come in handy. Whenever we
want to fetch an RSS feed, instead of doing it immediately, we can add a job to
a queue. The queue will be consumed by a thread pool, and we can pick the
amount of concurrency we want by adjusting the size of the thread pool. For
example, if we use a thread pool with only one thread in it (which is the
default), then our app will never fetch more than one RSS feed at the same
time.

Add a `:fetch-rss` queue like so:

```diff
;; src/com/eelchat/subscriptions.clj
;; ...
          (map #(assoc-result ctx %))
          (mapcat subscription-tx))))

+(defn fetch-rss-consumer [{:keys [biff/job] :as ctx}]
+  (biff/submit-tx ctx
+    (subscription-tx (assoc-result ctx job))))
+
 (def module
   {:tasks [{:task #'fetch-rss
-            :schedule #(every-n-minutes 5)}]})
+            :schedule #(every-n-minutes 5)}]
+   :queues [{:id :fetch-rss
+             :consumer #'fetch-rss-consumer}]})
```

Queues, like scheduled tasks, are only initialized at startup, so go ahead and
refresh the system to make the change take effect (`dev/repl.clj` ->
`(main/refresh)`). Then we'll modify the
`com.eelchat.app/on-new-subscription` transaction listener to have it
submit a job:

```diff
;; src/com/eelchat/app.clj
;; ...
 (defn on-new-subscription [{:keys [biff.xtdb/node] :as ctx} tx]
   (let [db-before (xt/db node {::xt/tx-id (dec (::xt/tx-id tx))})]
     (doseq [[op & args] (::xt/tx-ops tx)
             :when (= op ::xt/put)
             :let [[doc] args]
             :when (and (contains? doc :subscription/url)
                        (nil? (xt/entity db-before (:xt/id doc))))]
-      (future
-       (biff/submit-tx ctx
-         (sub/subscription-tx (sub/assoc-result ctx doc)))))))
+      (biff/submit-job ctx :fetch-rss doc))))

 (defn on-tx [ctx tx]
   (on-new-message ctx tx)
```

Voila! Try adding another subscription to one of your channels to
ensure everything still works. The latest post from the feed should show up
like it did before.

Let's modify our scheduled task to use the queue as well:

```diff
;; src/com/eelchat/subscriptions.clj
;; ...
                 :message/text (format-post post)}]))))

 (defn fetch-rss [{:keys [biff/db] :as ctx}]
-  (biff/submit-tx ctx
-    (->> (subscriptions-to-update db)
-         (map #(assoc-result ctx %))
-         (mapcat subscription-tx))))
+  (doseq [subscription (subscriptions-to-update db)]
+    (biff/submit-job ctx :fetch-rss subscription)))
```

There's a slight caveat here: what if our scheduled task adds a bunch of jobs to the queue,
and while they're still being processed, someone adds a subscription to their channel? As it is
now, there could be a significant lag before the new subscription gets fetched.

We'll fix this by adding a higher priority to jobs for new subscriptions. (The
default priority is 10, and lower numbers take higher priority.)

```diff
;; src/com/eelchat/app.clj
;; ...
 (defn on-new-subscription [{:keys [biff.xtdb/node] :as ctx} tx]
   (let [db-before (xt/db node {::xt/tx-id (dec (::xt/tx-id tx))})]
     (doseq [[op & args] (::xt/tx-ops tx)
             :when (= op ::xt/put)
             :let [[doc] args]
             :when (and (contains? doc :subscription/url)
                        (nil? (xt/entity db-before (:xt/id doc))))]
-      (biff/submit-job ctx :fetch-rss doc))))
+      (biff/submit-job ctx :fetch-rss (assoc doc :biff/priority 0)))))

 (defn on-tx [ctx tx]
   (on-new-message ctx tx)
```
