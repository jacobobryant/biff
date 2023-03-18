---
title: Communities
---

[View the code for this section](https://github.com/jacobobryant/eelchat/commit/bfff8fea51520b0b1ee7818865262725943c8d27).

Now that we have our landing page finished and deployed to production,
let's add all the bare-minimum essential features as quickly as we can.
This will be mostly CRUD.

We should start by thinking a bit about our **data model**. eelchat
will have at least five types of documents:

 - User
 - Community
 - Membership
 - Channel
 - Message

Users can create and join communities. A membership describes the relationship
between a user and a community: a user will have one membership document for
each community they're in, which will include things like roles. For example, a
user who creates a community will have an "admin" role in that community. A
community can have a collection of channels, and each channel can have a
collection of messages.

Go to `com.eelchat.schema` and change your schema to the following:

```clojure
;; src/com/eelchat/schema.clj
;; ...
(def schema
  {:user/id :uuid
   :user    [:map {:closed true}
             [:xt/id          :user/id]
             [:user/email     :string]
             [:user/joined-at inst?]]

   :comm/id   :uuid
   :community [:map {:closed true}
               [:xt/id      :comm/id]
               [:comm/title :string]]

   :mem/id     :uuid
   :membership [:map {:closed true}
                [:xt/id     :mem/id]
                [:mem/user  :user/id]
                [:mem/comm  :comm/id]
                [:mem/roles [:set [:enum :admin]]]]

   :chan/id :uuid
   :channel [:map {:closed true}
             [:xt/id      :chan/id]
             [:chan/title :string]
             [:chan/comm  :comm/id]]

   :msg/id  :uuid
   :message [:map {:closed true}
             [:xt/id          :msg/id]
             [:msg/mem        :mem/id]
             [:msg/text       :string]
             [:msg/channel    :chan/id]
             [:msg/created-at inst?]]})
```

As our application develops, we'll inevitably need to add more schema. But this
should be enough for now; no need to overthink it.

Head over to `com.eelchat.feat.app` and throw in a "New community" button:

```diff
;; src/com/eelchat/feat/app.clj
;; ...
         "Sign out"])
       "."]
      [:.h-6]
-     [:div "Thanks for joining the waitlist. "
-      "We'll let you know when eelchat is ready to use."])))
+     (biff/form
+      {:action "/community"}
+      [:button.btn {:type "submit"} "New community"]))))
+
+(defn new-community [{:keys [session] :as req}]
+  (let [comm-id (random-uuid)]
+    (biff/submit-tx req
+      [{:db/doc-type :community
+        :xt/id comm-id
+        :comm/title (str "Community #" (rand-int 1000))}
+       {:db/doc-type :membership
+        :mem/user (:uid session)
+        :mem/comm comm-id
+        :mem/roles #{:admin}}])
+    {:status 303
+     :headers {"Location" (str "/community/" comm-id)}}))
+
+(defn community [{:keys [biff/db path-params] :as req}]
+  (if-some [comm (xt/entity db (parse-uuid (:id path-params)))]
+    (ui/page
+     {}
+     [:p "Welcome to " (:comm/title comm)])
+    {:status 303
+     :headers {"location" "/app"}}))

 (def features
-  {:routes ["/app" {:middleware [mid/wrap-signed-in]}
-            ["" {:get app}]]})
+  {:routes ["" {:middleware [mid/wrap-signed-in]}
+            ["/app"           {:get app}]
+            ["/community"     {:post new-community}]
+            ["/community/:id" {:get community}]]})
```

Let's also tweak the default button style in `resources/tailwind.css` to match eelchat's
branding:

```diff
;; resources/tailwind.css
;; ...

 @layer components {
   .btn {
-    @apply bg-blue-500 hover:bg-blue-700 text-center py-2 px-4 rounded disabled:opacity-50 text-white;
+    @apply bg-teal-600 hover:bg-teal-800 text-center py-2 px-4 disabled:opacity-50 text-white;
   }
 }
```


Run `bb dev`, go to `localhost:8080`, and sign in if you aren't already. Then create a community:

![Screenshot of the "New community" button](/img/tutorial/create-community-1.png)

![Screenshot of the app after clicking the "New community" button](/img/tutorial/create-community-2.png)

So far so good. We've got two application pages, one at `/app` and the other at `/community/:id`. Before we go
further, let's create a template that'll be shared between all our application pages. The template will hold
all the common elements like navigation controls and such. It could start out something like this:

![Wireframe of the application layout](/img/tutorial/layout.png)

On every page, we'll want to know the list of communities the current user is
in so that we can populate the dropdown box in the upper left corner above. Let's
modify `com.eelchat.middleware/wrap-signed-in` so it loads the user's
memberships (and the associated communities) on each request:

```diff
;; src/com/eelchat/middleware.clj
-(ns com.eelchat.middleware)
+(ns com.eelchat.middleware
+  (:require [xtdb.api :as xt]))

 (defn wrap-redirect-signed-in [handler]
   (fn [{:keys [session] :as req}]
;; ...
       (handler req))))

 (defn wrap-signed-in [handler]
-  (fn [{:keys [session] :as req}]
-    (if (some? (:uid session))
-      (handler req)
+  (fn [{:keys [biff/db session] :as req}]
+    (if-some [user %%(xt/pull db%%
+                            %%'[* {(:mem/_user {:as :user/mems})%%
+                                 %%[* {:mem/comm [*]}]}]%%
+                            %%(:uid session))%%]
+      (handler (assoc req :user user))
       {:status 303
        :headers {"location" "/"}})))
```

That `xt/pull` call is a little complex; you may want to read up on
[XTDB pull queries](https://docs.xtdb.com/language-reference/datalog-queries/#pull).
Let's add a `pprint` call to the community page so you can see the result of the query:

```diff
;; src/com/eelchat/feat/app.clj
;; ...
     {:status 303
      :headers {"Location" (str "/community/" comm-id)}}))

-(defn community [{:keys [biff/db path-params] :as req}]
+(defn community [{:keys [biff/db user path-params] :as req}]
+  (biff/pprint user)
   (if-some [comm (xt/entity db (parse-uuid (:id path-params)))]
     (ui/page
      {}
```

Refresh the web page, and you should see something like this in your terminal:

```clojure
{:user/joined-at #inst "2022-11-04T22:35:13.851-00:00",
 :user/email "hello@example.com",
 :xt/id #uuid "78ccc870-df02-44a6-b224-99b36d754701",
 :user/mems
 ({:mem/user #uuid "78ccc870-df02-44a6-b224-99b36d754701",
   :mem/comm
   {:comm/title "Community #111",
    :xt/id #uuid "36906af5-9e1b-43ae-a9bf-854d77b14396"},
   :mem/roles #{:admin},
   :xt/id #uuid "7988e233-3a9b-45e8-ab65-0914e40b01e4"})}
```

That gives us everything we need to render the application template.
Go to `com.eelchat.ui` and add an `app-page` function:

```clojure
;; src/com/eelchat/ui.clj
;; ...
(defn app-page [{:keys [uri user] :as opts} & body]
  (base
   opts
   [:.flex.bg-orange-50
    [:.h-screen.w-80.p-3.pr-0.flex.flex-col.flex-grow
     [:select
      {:class '[text-sm
                cursor-pointer
                focus:border-teal-600
                focus:ring-teal-600]
       :onchange "window.location = this.value"}
      [:option {:value "/app"}
       "Select a community"]
      (for [{:keys [mem/comm]} (:user/mems user)
            :let [url (str "/community/" (:xt/id comm))]]
        [:option.cursor-pointer
         {:value url
          :selected (when (= url uri)
                      "selected")}
         (:comm/title comm)])]
     [:.grow]
     (biff/form
      {:action "/community"}
      [:button.btn.w-full {:type "submit"} "New community"])
     [:.h-3]
     [:.text-sm (:user/email user) " | "
      (biff/form
       {:action "/auth/signout"
        :class "inline"}
       [:button.text-teal-600.hover:text-teal-800 {:type "submit"}
        "Sign out"])]]
    [:.h-screen.w-full.p-3.flex.flex-col
     body]]))
```

Then use the new function in `com.eelchat.app`:

```diff
;; src/com/eelchat/feat/app.clj
;; ...
             [com.eelchat.ui :as ui]
             [xtdb.api :as xt]))

-(defn app [{:keys [session biff/db] :as req}]
-  (let [{:user/keys [email]} (xt/entity db (:uid session))]
-    (ui/page
-     {}
-     nil
-     [:div "Signed in as " email ". "
-      (biff/form
-       {:action "/auth/signout"
-        :class "inline"}
-       [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
-        "Sign out"])
-      "."]
-     [:.h-6]
-     (biff/form
-      {:action "/community"}
-      [:button.btn {:type "submit"} "New community"]))))
+(defn app [req]
+  (ui/app-page
+   req
+   [:p "Select a community, or create a new one."]))

 (defn new-community [{:keys [session] :as req}]
   (let [comm-id (random-uuid)]
;; ...
     {:status 303
      :headers {"Location" (str "/community/" comm-id)}}))

-(defn community [{:keys [biff/db user path-params] :as req}]
-  (biff/pprint user)
-  (if-some [comm (xt/entity db (parse-uuid (:id path-params)))]
-    (ui/page
-     {}
-     [:p "Welcome to " (:comm/title comm)])
+(defn community [{:keys [biff/db path-params] :as req}]
+  (if (some? (xt/entity db (parse-uuid (:id path-params))))
+    (ui/app-page
+     req
+     [:.border.border-neutral-600.p-3.bg-white.grow
+      "Messages window"]
+     [:.h-3]
+     [:.border.border-neutral-600.p-3.h-28.bg-white
+      "Compose window"])
     {:status 303
      :headers {"location" "/app"}}))
```

Voila:

![Screenshot of the new layout](/img/tutorial/layout-2.png)

Lastly, let's make it so you can join a community that someone else has created.
Copy the URL for your community (`http://localhost:8080/community/...`) and save it somewhere.
Sign out, then sign in again with a different address. Paste the community URL into your browser.

You should see the same page as before, like the one in the screenshot above.
Let's have `com.eelchat.feat.app/community` check to see if the current user is
a member already, and if not, show a join button:

```diff
;; src/com/eelchat/feat/app.clj
;; ...
     {:status 303
      :headers {"Location" (str "/community/" comm-id)}}))
 
+(defn join-community [{:keys [user community] :as req}]
+  (biff/submit-tx req
+    [{:db/doc-type :membership
+      :db.op/upsert {:mem/user (:xt/id user)
+                     :mem/comm (:xt/id community)}
+      :mem/roles [:db/default #{}]}])
+  {:status 303
+   :headers {"Location" (str "/community/" (:xt/id community))}})
+
-(defn community [{:keys [biff/db path-params] :as req}]
-  (if (some? (xt/entity db (parse-uuid (:id path-params))))
+(defn community [{:keys [biff/db user community] :as req}]
+  (let [member (some (fn [mem]
+                       (= (:xt/id community) (get-in mem [:mem/comm :xt/id])))
+                     (:user/mems user))]
     (ui/app-page
      req
-     [:.border.border-neutral-600.p-3.bg-white.grow
-      "Messages window"]
-     [:.h-3]
-     [:.border.border-neutral-600.p-3.h-28.bg-white
-      "Compose window"])
-    {:status 303
-     :headers {"location" "/app"}}))
+     (if member
+       [:<>
+        [:.border.border-neutral-600.p-3.bg-white.grow
+         "Messages window"]
+        [:.h-3]
+        [:.border.border-neutral-600.p-3.h-28.bg-white
+         "Compose window"]]
+       [:<>
+        [:.grow]
+        [:h1.text-3xl.text-center (:comm/title community)]
+        [:.h-6]
+        (biff/form
+         {:action (str "/community/" (:xt/id community) "/join")
+          :class "flex justify-center"}
+         [:button.btn {:type "submit"} "Join this community"])
+        [:div {:class "grow-[1.75]"}]]))))
+
+(defn wrap-community [handler]
+  (fn [{:keys [biff/db path-params] :as req}]
+    (if-some [community (xt/entity db (parse-uuid (:id path-params)))]
+      (handler (assoc req :community community))
+      {:status 303
+       :headers {"location" "/app"}})))
 
 (def features
   {:routes ["" {:middleware [mid/wrap-signed-in]}
             ["/app"           {:get app}]
             ["/community"     {:post new-community}]
-            ["/community/:id" {:get community}]]})
+            ["/community/:id" {:middleware [wrap-community]}
+             [""      {:get community}]
+             ["/join" {:post join-community}]]]})
```

![Screenshot of the join button](/img/tutorial/join-community.png)

Hit the button and you should be in!
