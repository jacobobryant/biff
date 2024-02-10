---
title: Channels
---

[View the code for this section](https://github.com/jacobobryant/eelchat/commit/ef3ed9c45f36899b7c56a200f64cbcc943c598e1).

Now that users can create and join communities, we're ready to let
community admins create and delete channels. We'll start by adding a "New
channel" button. But first, let's update `com.eelchat.app/wrap-community`
so it adds the current user's roles to the incoming request:

```diff
;; src/com/eelchat/app.clj
;; ...
         [:div {:class "grow-[1.75]"}]]))))

 (defn wrap-community [handler]
-  (fn [{:keys [biff/db path-params] :as ctx}]
+  (fn [{:keys [biff/db user path-params] :as ctx}]
     (if-some [community (xt/entity db (parse-uuid (:id path-params)))]
-      (handler (assoc ctx :community community))
+      (let [roles (->> (:user/memberships user)
+                       (filter (fn [membership]
+                                 (= (:xt/id community)
+                                    (get-in membership [:membership/community :xt/id]))))
+                       first
+                       :membership/roles)]
+        (handler (assoc ctx :community community :roles roles)))
       {:status 303
        :headers {"location" "/app"}})))
```

Now in `com.eelchat.ui`, we can check if the user has the `:admin` role and show the button
if so:

```diff
;; src/com/eelchat/ui.clj

-(defn app-page [{:keys [uri user] :as ctx} & body]
+(defn app-page [{:keys [uri user community roles] :as ctx} & body]
   (base
    ctx
    [:.flex.bg-orange-50
;; ...
                       url)}
          (:community/title community)])]
      [:.grow]
+     (when (contains? roles :admin)
+       [:<>
+        (biff/form
+         {:action (str "/community/" (:xt/id community) "/channel")}
+         [:button.btn.w-full {:type "submit"} "New channel"])
+        [:.h-3]])
      (biff/form
       {:action "/community"}
       [:button.btn.w-full {:type "submit"} "New community"])
```

![Screenshot of the "New channel" button](/img/tutorial/new-channel-button.png)

(If the button doesn't show up, you may need to sign back into the account that
created this community.)

Next we'll add a handler so that the button actually does something. We'll also add a dummy
`channel-page` handler:

```diff
;; src/com/eelchat/app.clj
;; ...
   {:status 303
    :headers {"Location" (str "/community/" (:xt/id community))}})
 
+(defn new-channel [{:keys [community roles] :as ctx}]
+  (if (and community (contains? roles :admin))
+    (let [channel-id (random-uuid)]
+     (biff/submit-tx ctx
+       [{:db/doc-type :channel
+         :xt/id channel-id
+         :channel/title (str "Channel #" (rand-int 1000))
+         :channel/community (:xt/id community)}])
+     {:status 303
+      :headers {"Location" (str "/community/" (:xt/id community) "/channel/" channel-id)}})
+    {:status 403
+     :body "Forbidden."}))
+
 (defn community [{:keys [biff/db user community] :as ctx}]
   (let [member (some (fn [membership]
                        (= (:xt/id community) (get-in membership [:membership/community :xt/id])))
;; ...
          [:button.btn {:type "submit"} "Join this community"])
         [:div {:class "grow-[1.75]"}]]))))
 
+(defn channel-page [ctx]
+  ;; We'll update this soon
+  (community ctx))
+
 (defn wrap-community [handler]
   (fn [{:keys [biff/db user path-params] :as ctx}]
     (if-some [community (xt/entity db (parse-uuid (:id path-params)))]
;; ...
       {:status 303
        :headers {"location" "/app"}})))
 
+(defn wrap-channel [handler]
+  (fn [{:keys [biff/db user community path-params] :as ctx}]
+    (let [channel (xt/entity db (parse-uuid (:channel-id path-params)))]
+      (if (= (:channel/community channel) (:xt/id community))
+        (handler (assoc ctx :channel channel))
+        {:status 303
+         :headers {"Location" (str "/community/" (:xt/id community))}}))))
+
 (def module
   {:routes ["" {:middleware [mid/wrap-signed-in]}
             ["/app"           {:get app}]
             ["/community"     {:post new-community}]
             ["/community/:id" {:middleware [wrap-community]}
              [""      {:get community}]
-             ["/join" {:post join-community}]]]})
+             ["/join" {:post join-community}]
+             ["/channel" {:post new-channel}]
+             ["/channel/:channel-id" {:middleware [wrap-channel]}
+              ["" {:get channel-page}]]]]})
```

Now let's update `com.eelchat.ui/app-page` so that it displays the channels
in the sidebar if you're a member of the community:

```diff
;; src/com/eelchat/ui.clj
;; ...
 (ns com.eelchat.ui
   (:require [cheshire.core :as cheshire]
             [clojure.java.io :as io]
             [com.eelchat.settings :as settings]
-            [com.biffweb :as biff]
+            [com.biffweb :as biff :refer [q]]
             [ring.middleware.anti-forgery :as csrf]))
 
;; ...
 
+(defn channels [{:keys [biff/db community roles]}]
+  (when (some? roles)
+    (sort-by
+     :channel/title
+     (q db
+        '{:find (pull channel [*])
+          :in [community]
+          :where [[channel :channel/community community]]}
+        (:xt/id community)))))
+
-(defn app-page [{:keys [uri user community roles] :as ctx} & body]
+(defn app-page [{:keys [biff/db uri user community roles channel] :as ctx} & body]
   (base
    ctx
    [:.flex.bg-orange-50
;; ...
           :selected (when (= url uri)
                       url)}
          (:community/title community)])]
+     [:.h-4]
+     (for [c (channels ctx)
+           :let [active (= (:xt/id c) (:xt/id channel))]]
+       [:.mt-3 (if active
+                 [:span.font-bold (:channel/title c)]
+                 [:a.link {:href (str "/community/" (:xt/id community)
+                                      "/channel/" (:xt/id c))}
+                  (:channel/title c)])])
      [:.grow]
      (when (contains? roles :admin)
        [:<>
```

If you create multiple channels, you should be able to navigate between them:

![Screenshot with several channels in the navigation sidebar](/img/tutorial/channels.png)

### Delete channels

Next we'll add a delete button for each channel. They'll only be visible if you're an admin.
Make a new `src/com/eelchat/ui/icons.clj` file, containing the free
[X icon](https://fontawesome.com/icons/x) from Font Awesome:

```clojure
;; src/com/eelchat/ui/icons.clj
(ns com.eelchat.ui.icons)

(def data
  {:x {:view-box "0 0 384 512", :path "M376.6 84.5c11.3-13.6 9.5-33.8-4.1-45.1s-33.8-9.5-45.1 4.1L192 206 56.6 43.5C45.3 29.9 25.1 28.1 11.5 39.4S-3.9 70.9 7.4 84.5L150.3 256 7.4 427.5c-11.3 13.6-9.5 33.8 4.1 45.1s33.8 9.5 45.1-4.1L192 306 327.4 468.5c11.3 13.6 31.5 15.4 45.1 4.1s15.4-31.5 4.1-45.1L233.7 256 376.6 84.5z"}})

(defn icon [k & [opts]]
  (let [{:keys [view-box path]} (data k)]
    [:svg.flex-shrink-0.inline
     (merge {:xmlns "http://www.w3.org/2000/svg"
             :viewBox view-box}
            opts)
     [:path {:fill "currentColor"
             :d path}]]))
```

Then modify `com.eelchat.ui/app-page` so it includes the delete buttons. We'll
do a little finagling to make the icon vertically aligned:

```diff
;; src/com/eelchat/ui.clj
;; ...
 (ns com.eelchat.ui
   (:require [cheshire.core :as cheshire]
             [clojure.java.io :as io]
             [clojure.string :as str]
             [com.eelchat.settings :as settings]
+            [com.eelchat.ui.icons :refer [icon]]
;; ...
(defn app-page [{:keys [biff/db uri user community roles channel] :as ctxs} & body]
   (base
    ctxs
    [:.flex.bg-orange-50
+    {:hx-headers (cheshire/generate-string
+                  {:x-csrf-token csrf/*anti-forgery-token*})}
     [:.h-screen.w-80.p-3.pr-0.flex.flex-col.flex-grow
      [:select
       {:class '[text-sm
;; ...
             :let [url (str "/community/" (:xt/id community))]]
         [:option.cursor-pointer
          {:value url
           :selected (str/starts-with? uri url)}
          (:community/title community)])]
      [:.h-4]
      (for [c (channels ctx)
-           :let [active (= (:xt/id c) (:xt/id channel))]]
+           :let [active (= (:xt/id c) (:xt/id channel))
+                 href (str "/community/" (:xt/id community)
+                           "/channel/" (:xt/id c))]]
-       [:.mt-3 (if active
-                 [:span.font-bold (:channel/title c)]
-                 [:a.link {:href (str "/community/" (:xt/id community)
-                                      "/channel/" (:xt/id c))}
-                  (:channel/title c)])])
+       [:.mt-4.flex.justify-between.leading-none
+        (if active
+          [:span.font-bold (:channel/title c)]
+          [:a.link {:href href}
+           (:channel/title c)])
+        (when (contains? roles :admin)
+          [:button.opacity-50.hover:opacity-100.flex.items-center
+           {:hx-delete href
+            :hx-confirm (str "Delete " (:channel/title c) "?")
+            :hx-target "closest div"
+            :hx-swap "outerHTML"
+            :_ (when active
+                 (str "on htmx:afterRequest set window.location to '/community/" (:xt/id community) "'"))}
+           (icon :x {:class "w-3 h-3"})])])
      [:.grow]
      (when (contains? roles :admin)
        [:<>
```

The `:hx-headers` value allows us to trigger an htmx request from the button
element without wrapping it in `biff/form`, which is normally responsible for
adding the CSRF token to your requests.

After we define the corresponding request handler, our delete buttons will be
fully functional:

```diff
;; src/com/eelchat/app.clj
;; ...
     {:status 403
      :body "Forbidden."}))
 
+(defn delete-channel [{:keys [channel roles] :as ctx}]
+  (when (contains? roles :admin)
+    (biff/submit-tx ctx
+      [{:db/op :delete
+        :xt/id (:xt/id channel)}]))
+  [:<>])
+
 (defn community [{:keys [biff/db user community] :as ctx}]
   (let [member (some (fn [membership]
                        (= (:xt/id community) (get-in membership [:membership/community :xt/id])))
;; ...
              ["/join" {:post join-community}]
              ["/channel" {:post new-channel}]
              ["/channel/:channel-id" {:middleware [wrap-channel]}
-              ["" {:get channel-page}]]]]})
+              ["" {:get channel-page
+                   :delete delete-channel}]]]]})
```

In this case, `[:<>]` is an easy way to return an empty response
(see the [Rum docs](https://github.com/tonsky/rum#react-fragment)).

Voila:

![Screen recording of the delete buttons](/img/tutorial/delete-button.gif)
