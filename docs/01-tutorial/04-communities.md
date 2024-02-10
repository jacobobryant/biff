---
title: Communities
---

[View the code for this section](https://github.com/jacobobryant/eelchat/commit/60f42d56c79a0f8fcb8b2b1e6b825ddc95c20cf1).

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
   :user [:map {:closed true}
          [:xt/id          :user/id]
          [:user/email     :string]
          [:user/joined-at inst?]]

   :community/id :uuid
   :community [:map {:closed true}
               [:xt/id           :community/id]
               [:community/title :string]]

   :membership/id :uuid
   :membership [:map {:closed true}
                [:xt/id                :membership/id]
                [:membership/user      :user/id]
                [:membership/community :community/id]
                [:membership/roles     [:set [:enum :admin]]]]

   :channel/id :uuid
   :channel [:map {:closed true}
             [:xt/id             :channel/id]
             [:channel/title     :string]
             [:channel/community :community/id]]

   :message/id :uuid
   :message [:map {:closed true}
             [:xt/id              :message/id]
             [:message/membership :membership/id]
             [:message/text       :string]
             [:message/channel    :channel/id]
             [:message/created-at inst?]]})
```

As our application develops, we'll inevitably need to add more schema. But this
should be enough for now; no need to overthink it.

Head over to `com.eelchat.app` and throw in a "New community" button:

```diff
;; src/com/eelchat/app.clj
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
+(defn new-community [{:keys [session] :as ctx}]
+  (let [community-id (random-uuid)]
+    (biff/submit-tx ctx
+      [{:db/doc-type :community
+        :xt/id community-id
+        :community/title (str "Community #" (rand-int 1000))}
+       {:db/doc-type :membership
+        :membership/user (:uid session)
+        :membership/community community-id
+        :membership/roles #{:admin}}])
+    {:status 303
+     :headers {"Location" (str "/community/" community-id)}}))
+
+(defn community [{:keys [biff/db path-params] :as ctx}]
+  (if-some [community (xt/entity db (parse-uuid (:id path-params)))]
+    (ui/page
+     {}
+     [:p "Welcome to " (:community/title community)])
+    {:status 303
+     :headers {"location" "/app"}}))

 (def module
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


Run `clj -M:dev dev`, go to `localhost:8080`, and sign in if you aren't already. Then create a community:

![Screenshot of the "New community" button](/img/tutorial/create-community-1.png)

![Screenshot of the app after clicking the "New community" button](/img/tutorial/create-community-2.png)

So far so good. We've got two application pages, one at `/app` and the other at `/community/:id`. Before we go
further, let's create a template that'll be shared between all our application pages. The template will hold
all the common elements like navigation controls and such. It could start out something like this:

![Wireframe of the application layout](/img/tutorial/layout.png)

On every page, we'll want to know the list of communities the current user is
in so that we can populate the dropdown box in the upper left corner above. Let's
modify `com.eelchat.middleware/wrap-signed-in` so it loads the user's
memberships (and the associated communities) on each request. We'll also change the redirect
location to `/` instead of `/signin` while we're at it:

```diff
;; src/com/eelchat/middleware.clj
 (ns com.eelchat.middleware
   (:require [com.biffweb :as biff]
             [muuntaja.middleware :as muuntaja]
             [ring.middleware.anti-forgery :as csrf]
-            [ring.middleware.defaults :as rd]))
+            [ring.middleware.defaults :as rd]
+            [xtdb.api :as xt]))
;; ...
 (defn wrap-signed-in [handler]
-  (fn [{:keys [session] :as ctx}]
-    (if (some? (:uid session))
-      (handler ctx)
-      {:status 303
-       :headers {"location" "/signin?error=not-signed-in"}})))
+  (fn [{:keys [biff/db session] :as ctx}]
+    (if-some [user %%(xt/pull db%%
+                            %%'[* {(:membership/_user {:as :user/memberships})%%
+                                 %%[* {:membership/community [*]}]}]%%
+                            %%(:uid session))%%]
+      (handler (assoc ctx :user user))
+      {:status 303
+       :headers {"location" "/?error=not-signed-in"}})))
```

That `xt/pull` call is a little complex; you may want to read up on
[XTDB pull queries](https://docs.xtdb.com/language-reference/datalog-queries/#pull).
Let's add a `pprint` call to the community page so you can see the result of the query:

```diff
;; src/com/eelchat/app.clj
;; ...
     {:status 303
      :headers {"Location" (str "/community/" community-id)}}))

-(defn community [{:keys [biff/db path-params] :as ctx}]
+(defn community [{:keys [biff/db user path-params] :as ctx}]
+  (biff/pprint user)
   (if-some [community (xt/entity db (parse-uuid (:id path-params)))]
     (ui/page
      {}
```

Refresh the web page, and you should see something like this in your terminal:

```clojure
{:user/joined-at #inst "2022-11-04T22:35:13.851-00:00",
 :user/email "hello@example.com",
 :xt/id #uuid "78ccc870-df02-44a6-b224-99b36d754701",
 :user/memberships
 ({:membership/user #uuid "78ccc870-df02-44a6-b224-99b36d754701",
   :membership/community
   {:community/title "Community #111",
    :xt/id #uuid "36906af5-9e1b-43ae-a9bf-854d77b14396"},
   :membership/roles #{:admin},
   :xt/id #uuid "7988e233-3a9b-45e8-ab65-0914e40b01e4"})}
```

That gives us everything we need to render the application template.
Go to `com.eelchat.ui` and add an `app-page` function:

```clojure
;; src/com/eelchat/ui.clj
 (ns com.eelchat.ui
   (:require [cheshire.core :as cheshire]
             [clojure.java.io :as io]
+            [clojure.string :as str]
;; ...
(defn app-page [{:keys [uri user] :as ctx} & body]
  (base
   ctx
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
      (for [{:keys [membership/community]} (:user/memberships user)
            :let [url (str "/community/" (:xt/id community))]]
        [:option.cursor-pointer
         {:value url
          :selected (str/starts-with? uri url)}
         (:community/title community)])]
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
;; src/com/eelchat/app.clj
;; ...
             [com.eelchat.ui :as ui]
             [xtdb.api :as xt]))

-(defn app [{:keys [session biff/db] :as ctx}]
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
+(defn app [ctx]
+  (ui/app-page
+   ctx
+   [:p "Select a community, or create a new one."]))

 (defn new-community [{:keys [session] :as ctx}]
   (let [community-id (random-uuid)]
;; ...
     {:status 303
      :headers {"Location" (str "/community/" community-id)}}))

-(defn community [{:keys [biff/db user path-params] :as ctx}]
-  (biff/pprint user)
-  (if-some [community (xt/entity db (parse-uuid (:id path-params)))]
-    (ui/page
-     {}
-     [:p "Welcome to " (:community/title community)])
-     {:status 303
-      :headers {"location" "/app"}}))
+(defn community [{:keys [biff/db path-params] :as ctx}]
+  (if (some? (xt/entity db (parse-uuid (:id path-params))))
+    (ui/app-page
+     ctx
+     [:.border.border-neutral-600.p-3.bg-white.grow
+      "Messages window"]
+     [:.h-3]
+     [:.border.border-neutral-600.p-3.h-28.bg-white
+      "Compose window"])
+    {:status 303
+     :headers {"location" "/app"}}))
```

Voila:

![Screenshot of the new layout](/img/tutorial/layout-2.png)

Lastly, let's make it so you can join a community that someone else has created.
Copy the URL for your community (`http://localhost:8080/community/...`) and save it somewhere.
Sign out, then sign in again with a different address. Paste the community URL into your browser.

You should see the same page as before, like the one in the screenshot above.
Let's have `com.eelchat.app/community` check to see if the current user is
a member already, and if not, show a join button:

```diff
;; src/com/eelchat/app.clj
;; ...
     {:status 303
      :headers {"Location" (str "/community/" community-id)}}))
 
+(defn join-community [{:keys [user community] :as ctx}]
+  (biff/submit-tx ctx
+    [{:db/doc-type :membership
+      :db.op/upsert {:membership/user (:xt/id user)
+                     :membership/community (:xt/id community)}
+      :membership/roles [:db/default #{}]}])
+  {:status 303
+   :headers {"Location" (str "/community/" (:xt/id community))}})
+
-(defn community [{:keys [biff/db path-params] :as ctx}]
-  (if (some? (xt/entity db (parse-uuid (:id path-params))))
+(defn community [{:keys [biff/db user community] :as ctx}]
+  (let [member (some (fn [membership]
+                       (= (:xt/id community) (get-in membership [:membership/community :xt/id])))
+                     (:user/memberships user))]
     (ui/app-page
      ctx
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
+        [:h1.text-3xl.text-center (:community/title community)]
+        [:.h-6]
+        (biff/form
+         {:action (str "/community/" (:xt/id community) "/join")
+          :class "flex justify-center"}
+         [:button.btn {:type "submit"} "Join this community"])
+        [:div {:class "grow-[1.75]"}]]))))
+
+(defn wrap-community [handler]
+  (fn [{:keys [biff/db path-params] :as ctx}]
+    (if-some [community (xt/entity db (parse-uuid (:id path-params)))]
+      (handler (assoc ctx :community community))
+      {:status 303
+       :headers {"location" "/app"}})))
 
 (def module
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
