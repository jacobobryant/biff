---
title: Inbound RSS
---

[View the code for this section](https://github.com/jacobobryant/eelchat/commit/5229157b00ba01f94e16c78f65354af75fb38a4d).

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
              [:chan/title :string]
              [:chan/comm  :comm/id]]

+   :sub/id       :uuid
+   :subscription [:map {:closed true}
+                  [:xt/id             :sub/id]
+                  [:sub/url           :string]
+                  [:sub/chan          :chan/id]
+                  [:sub/last-post-uri {:optional true} :string]
+                  [:sub/fetched-at    {:optional true} inst?]
+                  [:sub/last-modified {:optional true} :string]
+                  [:sub/etag          {:optional true} :string]]
+
    :msg/id  :uuid
    :message [:map {:closed true}
              [:xt/id          :msg/id]
```

`:sub/last-modified` and `:sub/etag` will hold the values of their respective
response headers whenever we fetch the RSS feed. This will let us avoid
downloading the feed when there haven't been any new items yet.
`:sub/last-post-uri` will help us double check if we've already seen the feed's
most recent item or not.

Next, the boring part: we need some UI for subscribing to RSS feeds. Let's
take the lazy way out and implement it as a set of chat commands. Community
admins will be able to add subscriptions by typing `/subscribe [url]`. You can
view subscriptions by typing `/subscriptions`, and you can unsubscribe via
`/unsubscribe [url]`. This way we don't have to figure out where to stick another form
in the UI.

Our RSS bot will need to write messages of its own to the channel (both to provide output for
chat commands and to display new RSS items), so let's add `:system` as a possible value for
the `:msg/mem` key:

```diff
;; src/com/eelchat/schema.clj
;; ...
    :msg/id  :uuid
    :message [:map {:closed true}
              [:xt/id          :msg/id]
-             [:msg/mem        :mem/id]
+             [:msg/mem        [:or :mem/id [:enum :system]]]
              [:msg/text       :string]
              [:msg/channel    :chan/id]
              [:msg/created-at inst?]
```

We'll need to update our message rendering code to handle the new value. Let's just get
that out of the way now:

```diff
;; src/com/eelchat/feat/app.clj
;; ...
         [:div {:class "grow-[1.75]"}]]))))

 (defn message-view [{:msg/keys [mem text created-at]}]
-  (let [username (str "User " (subs (str mem) 0 4))]
+  (let [username (if (= :system mem)
+                   "🎅🏻 System 🎅🏻"
+                   (str "User " (subs (str mem) 0 4)))]
     [:div
      [:.text-sm
       [:span.font-bold username]
```

Now we'll modify the `com.eelchat.feat.app/new-message` function to have it
inspect the messages and execute commands when appropriate. Let's add a `command-tx`
function that returns a transaction representing the result of the command (which can
be empty if there was no command to execute):

```diff
;; src/com/eelchat/feat/app.clj
;; ...
   (:require [com.biffweb :as biff :refer [q]]
             [com.eelchat.middleware :as mid]
             [com.eelchat.ui :as ui]
+            [clojure.string :as str]
             [ring.adapter.jetty9 :as jetty]
             [rum.core :as rum]
             [xtdb.api :as xt]))
;; ...
       [:span.text-gray-600 (biff/format-date created-at "d MMM h:mm aa")]]
      [:p.whitespace-pre-wrap.mb-6 text]]))
 
+(defn command-tx [{:keys [biff/db channel roles params]}]
+  (let [subscribe-url (second (re-find #"^/subscribe ([^\s]+)" (:text params)))
+        unsubscribe-url (second (re-find #"^/unsubscribe ([^\s]+)" (:text params)))
+        list-command (= (str/trimr (:text params)) "/list")
+        message (fn [text]
+                  {:db/doc-type :message
+                   :msg/mem :system
+                   :msg/channel (:xt/id channel)
+                   :msg/text text
+                   ;; Make sure this message comes after the user's message.
+                   :msg/created-at (biff/add-seconds (java.util.Date.) 1)})]
+    (cond
+     (not (contains? roles :admin))
+     nil
+
+     subscribe-url
+     [{:db/doc-type :subscription
+       :db.op/upsert {:sub/url subscribe-url
+                      :sub/chan (:xt/id channel)}}
+      (message (str "Subscribed to " subscribe-url))]
+
+     unsubscribe-url
+     [{:db/op :delete
+       :xt/id (biff/lookup-id db :sub/chan (:xt/id channel) :sub/url unsubscribe-url)}
+      (message (str "Unsubscribed from " unsubscribe-url))]
+
+     list-command
+     [(message (apply
+                str
+                "Subscriptions:"
+                (for [url (->> (q db
+                                  '{:find (pull sub [:sub/url])
+                                    :in [channel]
+                                    :where [[sub :sub/chan channel]]}
+                                  (:xt/id channel))
+                               (map :sub/url)
+                               sort)]
+                  (str "\n - " url))))])))
+
 (defn new-message [{:keys [channel mem params] :as req}]
   (let [msg {:xt/id (random-uuid)
              :msg/mem (:xt/id mem)
              :msg/channel (:xt/id channel)
              :msg/created-at (java.util.Date.)
              :msg/text (:text params)}]
     (biff/submit-tx (assoc req :biff.xtdb/retry false)
-      [(assoc msg :db/doc-type :message)])
+      (concat [(assoc msg :db/doc-type :message)]
+              (command-tx req)))
     (message-view msg)))

 (defn channel-page [{:keys [biff/db community channel] :as req}]
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
  :deps {com.biffweb/biff {:git/url "https://github.com/jacobobryant/biff" :sha "9d725ba74514032b3a6f86affe16f8d3c9693135"}
         camel-snake-kebab/camel-snake-kebab {:mvn/version "0.4.3"}
+        remus/remus {:mvn/version "0.2.2"}
+        org.jsoup/jsoup {:mvn/version "1.11.3"}
         org.slf4j/slf4j-simple {:mvn/version "2.0.0-alpha5"}}}
```

(Sometimes dependencies can be added automatically, but in this case you'll
need to hit Ctrl-C and run `bb dev` again.)

We'll use Remus to fetch and parse the RSS feeds, and we'll use jsoup to
convert the posts' HTML content to plain text for our excerpts.

Now create a new namespace `com.eelchat.feat.subscriptions` with the following
contents:

```clojure
;; src/com/eelchat/feat/subscriptions.clj
(ns com.eelchat.feat.subscriptions
  (:require [com.biffweb :as biff :refer [q]]
            [remus :as remus])
  (:import [org.jsoup Jsoup]))

(defn every-n-minutes [n]
  (iterate #(biff/add-seconds % (* n 60)) (java.util.Date.)))

(defn subs-to-update [db]
  (q db
     '{:find (pull sub [*])
       :in [t]
       :where [[sub :sub/url]
               [(get-attr sub :sub/fetched-at #inst "1970")
                [fetched-at ...]]
               [(<= fetched-at t)]]}
     (biff/add-seconds (java.util.Date.) (* -60 30))))

(defn assoc-result [{:keys [biff/base-url]} {:sub/keys [url last-modified etag] :as sub}]
  (assoc sub ::result (biff/catchall-verbose
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

(defn sub-tx [{:sub/keys [chan last-post-uri] :keys [xt/id ::result]}]
  (let [post (-> result :feed :entries first)
        uri ((some-fn :uri :link) post)]
    (concat [(biff/assoc-some
              {:db/doc-type :subscription
               :db/op :update
               :xt/id id
               :sub/fetched-at :db/now}
              :sub/last-post-uri uri
              :sub/last-modified (get-in result [:response :headers "Last-Modified"])
              :sub/etag (get-in result [:response :headers "Etag"]))]
            (when (and (some? uri) (not= uri last-post-uri))
              [{:db/doc-type :message
                :msg/mem :system
                :msg/channel chan
                :msg/created-at :db/now
                :msg/text (format-post post)}]))))

(defn fetch-rss [{:keys [biff/db] :as sys}]
  (biff/submit-tx sys
    (->> (subs-to-update db)
         (map #(assoc-result sys %))
         (mapcat sub-tx))))

(def features
  {:tasks [{:task #'fetch-rss
            :schedule #(every-n-minutes 5)}]})
```

Then register the new namespace in your app:

```diff
;; src/com/eelchat.clj
;; ...
             [com.eelchat.feat.app :as app]
             [com.eelchat.feat.auth :as auth]
             [com.eelchat.feat.home :as home]
+            [com.eelchat.feat.subscriptions :as sub]
             [com.eelchat.schema :refer [malli-opts]]
             [clojure.java.io :as io]
             [clojure.string :as str]
;; ...
 (def features
   [app/features
    auth/features
+   sub/features
    home/features])

 (def routes [["" {:middleware [anti-forgery/wrap-anti-forgery
```

Scheduled tasks are only started when the system starts, so for our new task to
take effect, you'll need to go to `com.eelchat.repl` and evaluate the
`(biff/fix-print (biff/refresh))` form. (Again, you can alternatively hit
Ctrl-C in the terminal and re-run `bb dev`, but it'll be slower. However
sometimes that's a good option if you accidentally bork something and
`biff/refresh` doesn't work.)

Once the system starts up, it will run our scheduled task
immediately. If you already added a subscription to one of your channels, you
should see the latest post in a message. (You may need to refresh the page in
case our websocket connection was still being re-established when the message
was sent.)

![Screenshot of a channel containing an RSS post from our scheduled task](/img/tutorial/task-output.png)

If you add a new subscription and want to trigger the scheduled task
immediately without waiting five minutes, you can head over to
`com.eelchat.repl` and add
`(com.eelchat.feat.subscriptions/fetch-rss (get-sys))`
somewhere within the `comment` form. Evaluate it to fetch the feeds.

That being said, it would be nice if eelchat fetched new subscriptions right
away automatically. Let's add another transaction listener that will do just
that whenever there's a new subscription document:

```diff
;; src/com/eelchat/feat/app.clj
;; ...
 (ns com.eelchat.feat.app
   (:require [com.biffweb :as biff :refer [q]]
+            [com.eelchat.feat.subscriptions :as sub]
             [com.eelchat.middleware :as mid]
             [com.eelchat.ui :as ui]
             [clojure.string :as str]
;; ...
             :when (not= mem-id (:msg/mem doc))]
       (jetty/send! client html))))

+(defn on-new-subscription [{:keys [biff.xtdb/node] :as sys} tx]
+  (let [db-before (xt/db node {::xt/tx-id (dec (::xt/tx-id tx))})]
+    (doseq [[op & args] (::xt/tx-ops tx)
+            :when (= op ::xt/put)
+            :let [[doc] args]
+            :when (and (contains? doc :sub/url)
+                       (nil? (xt/entity db-before (:xt/id doc))))]
+      (future
+       (biff/submit-tx sys
+         (sub/sub-tx (sub/assoc-result sys doc)))))))
+
+(defn on-tx [sys tx]
+  (on-new-message sys tx)
+  (on-new-subscription sys tx))
+
 (defn wrap-community [handler]
   (fn [{:keys [biff/db user path-params] :as req}]
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

This is where Biff's in-memory
[queues](https://biffweb.com/docs/reference/queues/) come in handy. Whenever we
want to fetch an RSS feed, instead of doing it immediately, we can add a job to
a queue. The queue will be consumed by a thread pool, and we can pick the
amount of concurrency we want by adjusting the size of the thread pool. For
example, if we use a thread pool with only one thread in it (which is the
default), then our app will never fetch more than one RSS feed at the same
time.

Add a `:fetch-rss` queue like so:

```diff
;; src/com/eelchat/feat/subscriptions.clj
;; ...
          (map #(assoc-result sys %))
          (mapcat sub-tx))))

+(defn fetch-rss-consumer [{:keys [biff/job] :as sys}]
+  (biff/submit-tx sys
+    (sub-tx (assoc-result sys job))))
+
 (def features
   {:tasks [{:task #'fetch-rss
-            :schedule #(every-n-minutes 5)}]})
+            :schedule #(every-n-minutes 5)}]
+   :queues [{:id :fetch-rss
+             :consumer #'fetch-rss-consumer}]})
```

Queues, like scheduled tasks, are only initialized at startup, so go ahead and
refresh the system to make the change take effect (`com.eelchat.repl` ->
`(biff/fix-print (biff/refresh))`). Then we'll modify the
`com.eelchat.feat.app/on-new-subscription` transaction listener to have it
submit a job:

```diff
;; src/com/eelchat/feat/app.clj
;; ...
 (defn on-new-subscription [{:keys [biff.xtdb/node] :as sys} tx]
   (let [db-before (xt/db node {::xt/tx-id (dec (::xt/tx-id tx))})]
     (doseq [[op & args] (::xt/tx-ops tx)
             :when (= op ::xt/put)
             :let [[doc] args]
             :when (and (contains? doc :sub/url)
                        (nil? (xt/entity db-before (:xt/id doc))))]
-      (future
-       (biff/submit-tx sys
-         (sub/sub-tx (sub/assoc-result sys doc)))))))
+      (biff/submit-job sys :fetch-rss doc))))

 (defn on-tx [sys tx]
   (on-new-message sys tx)
```

Couldn't be easier! Try adding another subscription to one of your channels to
ensure everything still works. The latest post from the feed should show up
like it did before.

Let's modify our scheduled task to use the queue as well:

```diff
;; src/com/eelchat/feat/subscriptions.clj
;; ...
                 :msg/text (format-post post)}]))))

 (defn fetch-rss [{:keys [biff/db] :as sys}]
-  (biff/submit-tx sys
-    (->> (subs-to-update db)
-         (map #(assoc-result sys %))
-         (mapcat sub-tx))))
+  (doseq [sub (subs-to-update db)]
+    (biff/submit-job sys :fetch-rss sub)))
```

There's a slight caveat here: what if our scheduled task adds a bunch of jobs to the queue,
and while they're still being processed, someone adds a subscription to their channel? As it is
now, there could be a significant lag before the new subscription gets fetched.

We'll fix this by adding a higher priority to jobs for new subscriptions. (The
default priority is 10, and lower numbers take higher priority.)

```diff
;; src/com/eelchat/feat/app.clj
;; ...
             :let [[doc] args]
             :when (and (contains? doc :sub/url)
                        (nil? (xt/entity db-before (:xt/id doc))))]
-      (biff/submit-job sys :fetch-rss doc))))
+      (biff/submit-job sys :fetch-rss (assoc doc :biff/priority 0)))))

 (defn on-tx [sys tx]
   (on-new-message sys tx)
```
