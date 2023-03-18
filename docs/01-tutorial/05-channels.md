---
title: Channels
---

[View the code for this section](https://github.com/jacobobryant/eelchat/commit/ee08c1c0f12d8d9ff6f8f606cdf6eb9c40426cd7).

Now that users can create and join communities, we're ready to let
community admins create and delete channels. We'll start by adding a "New
channel" button. But first, let's update `com.eelchat.feat.app/wrap-community`
so it adds the current user's roles to the incoming request:

```diff
;; src/com/eelchat/feat/app.clj
;; ...
         [:div {:class "grow-[1.75]"}]]))))

 (defn wrap-community [handler]
-  (fn [{:keys [biff/db path-params] :as req}]
+  (fn [{:keys [biff/db user path-params] :as req}]
     (if-some [community (xt/entity db (parse-uuid (:id path-params)))]
-      (handler (assoc req :community community))
+      (let [roles (->> (:user/mems user)
+                       (filter (fn [mem]
+                                 (= (:xt/id community) (get-in mem [:mem/comm :xt/id]))))
+                       first
+                       :mem/roles)]
+        (handler (assoc req :community community :roles roles)))
       {:status 303
        :headers {"location" "/app"}})))
```

Now in `com.eelchat.ui`, we can check if the user has the `:admin` role and show the button
if so:

```diff
;; src/com/eelchat/ui.clj
;; ...
      body]
     [:div {:class "grow-[2]"}]]))

-(defn app-page [{:keys [uri user] :as opts} & body]
+(defn app-page [{:keys [uri user community roles] :as opts} & body]
   (base
    opts
    [:.flex.bg-orange-50
;; ...
                       url)}
          (:comm/title comm)])]
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
;; src/com/eelchat/feat/app.clj
;; ...
   {:status 303
    :headers {"Location" (str "/community/" (:xt/id community))}})
 
+(defn new-channel [{:keys [community roles] :as req}]
+  (if (and community (contains? roles :admin))
+    (let [chan-id (random-uuid)]
+     (biff/submit-tx req
+       [{:db/doc-type :channel
+         :xt/id chan-id
+         :chan/title (str "Channel #" (rand-int 1000))
+         :chan/comm (:xt/id community)}])
+     {:status 303
+      :headers {"Location" (str "/community/" (:xt/id community) "/channel/" chan-id)}})
+    {:status 403
+     :body "Forbidden."}))
+
 (defn community [{:keys [biff/db user community] :as req}]
   (let [member (some (fn [mem]
                        (= (:xt/id community) (get-in mem [:mem/comm :xt/id])))
;; ...
          [:button.btn {:type "submit"} "Join this community"])
         [:div {:class "grow-[1.75]"}]]))))
 
+(defn channel-page [req]
+  ;; We'll update this soon
+  (community req))
+
 (defn wrap-community [handler]
   (fn [{:keys [biff/db user path-params] :as req}]
     (if-some [community (xt/entity db (parse-uuid (:id path-params)))]
;; ...
       {:status 303
        :headers {"location" "/app"}})))
 
+(defn wrap-channel [handler]
+  (fn [{:keys [biff/db user community path-params] :as req}]
+    (let [channel (xt/entity db (parse-uuid (:chan-id path-params)))]
+      (if (= (:chan/comm channel) (:xt/id community))
+        (handler (assoc req :channel channel))
+        {:status 303
+         :headers {"Location" (str "/community/" (:xt/id community))}}))))
+
 (def features
   {:routes ["" {:middleware [mid/wrap-signed-in]}
             ["/app"           {:get app}]
             ["/community"     {:post new-community}]
             ["/community/:id" {:middleware [wrap-community]}
              [""      {:get community}]
-             ["/join" {:post join-community}]]]})
+             ["/join" {:post join-community}]
+             ["/channel" {:post new-channel}]
+             ["/channel/:chan-id" {:middleware [wrap-channel]}
+              ["" {:get channel-page}]]]]})
```

Now let's update `com.eelchat.ui/app-page` so that it displays the channels
in the sidebar if you're a member of the community:

```diff
;; src/com/eelchat/ui.clj
;; ...
 (ns com.eelchat.ui
   (:require [clojure.java.io :as io]
-            [com.biffweb :as biff]))
+            [com.biffweb :as biff :refer [q]]))
 
 (defn css-path []
   (if-some [f (io/file (io/resource "public/css/main.css"))]
;; ...
      body]
     [:div {:class "grow-[2]"}]]))
 
+(defn channels [{:keys [biff/db community roles]}]
+  (when (some? roles)
+    (sort-by
+     :chan/title
+     (q db
+        '{:find (pull channel [*])
+          :in [comm]
+          :where [[channel :chan/comm comm]]}
+        (:xt/id community)))))
+
-(defn app-page [{:keys [uri user community roles] :as opts} & body]
+(defn app-page [{:keys [biff/db uri user community roles channel] :as opts} & body]
   (base
    opts
    [:.flex.bg-orange-50
;; ...
           :selected (when (= url uri)
                       url)}
          (:comm/title comm)])]
+     [:.h-4]
+     (for [chan (channels opts)
+           :let [active (= (:xt/id chan) (:xt/id channel))]]
+       [:.mt-3 (if active
+                 [:span.font-bold (:chan/title chan)]
+                 [:a.link {:href (str "/community/" (:xt/id community)
+                                      "/channel/" (:xt/id chan))}
+                  (:chan/title chan)])])
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
do a little finagling to make the icon vertically aligned. And while we're at
it, let's fix the community drop-down box so it displays the current community
correctly when you're on a channel page:

```diff
;; src/com/eelchat/ui.clj
;; ...
 (ns com.eelchat.ui
-  (:require [clojure.java.io :as io]
-            [com.biffweb :as biff :refer [q]]))
+  (:require [cheshire.core :as cheshire]
+            [clojure.java.io :as io]
+            [clojure.string :as str]
+            [com.eelchat.ui.icons :refer [icon]]
+            [com.biffweb :as biff :refer [q]]
+            [ring.middleware.anti-forgery :as anti-forgery]))
 
;; ...
(defn app-page [{:keys [biff/db uri user community roles channel] :as opts} & body]
   (base
    opts
    [:.flex.bg-orange-50
+    {:hx-headers (cheshire/generate-string
+                  {:x-csrf-token anti-forgery/*anti-forgery-token*})}
     [:.h-screen.w-80.p-3.pr-0.flex.flex-col.flex-grow
      [:select
       {:class '[text-sm
;; ...
             :let [url (str "/community/" (:xt/id comm))]]
         [:option.cursor-pointer
          {:value url
-          :selected (when (= url uri)
-                      url)}
+          :selected (when (str/starts-with? uri url)
+                      "selected")}
          (:comm/title comm)])]
      [:.h-4]
      (for [chan (channels opts)
-           :let [active (= (:xt/id chan) (:xt/id channel))]]
+           :let [active (= (:xt/id chan) (:xt/id channel))
+                 href (str "/community/" (:xt/id community)
+                           "/channel/" (:xt/id chan))]]
-       [:.mt-3 (if active
-                 [:span.font-bold (:chan/title chan)]
-                 [:a.link {:href (str "/community/" (:xt/id community)
-                                      "/channel/" (:xt/id chan))}
-                  (:chan/title chan)])])
+       [:.mt-4.flex.justify-between.leading-none
+        (if active
+          [:span.font-bold (:chan/title chan)]
+          [:a.link {:href href}
+           (:chan/title chan)])
+        (when (contains? roles :admin)
+          [:button.opacity-50.hover:opacity-100.flex.items-center
+           {:hx-delete href
+            :hx-confirm (str "Delete " (:chan/title chan) "?")
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
;; src/com/eelchat/feat/app.clj
;; ...
     {:status 403
      :body "Forbidden."}))
 
+(defn delete-channel [{:keys [channel roles] :as req}]
+  (when (contains? roles :admin)
+    (biff/submit-tx req
+      [{:db/op :delete
+        :xt/id (:xt/id channel)}]))
+  [:<>])
+
 (defn community [{:keys [biff/db user community] :as req}]
   (let [member (some (fn [mem]
                        (= (:xt/id community) (get-in mem [:mem/comm :xt/id])))
;; ...
              ["/join" {:post join-community}]
              ["/channel" {:post new-channel}]
              ["/channel/:chan-id" {:middleware [wrap-channel]}
-              ["" {:get channel-page}]]]]})
+              ["" {:get channel-page
+                   :delete delete-channel}]]]]})
```

In this case, `[:<>]` is an easy way to return an empty response
(see the [Rum docs](https://github.com/tonsky/rum#react-fragment)).

Voila:

![Screen recording of the delete buttons](/img/tutorial/delete-button.gif)
