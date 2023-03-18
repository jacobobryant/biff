---
title: Start a new project
---

[View the code for this section](https://github.com/jacobobryant/eelchat/commit/b28580129acabe01857971307be0a73f95ccd823).

As with any Biff project, we'll need to run the new project script. We'll add a
`tutorial` argument so that your project will be based on the same version of
Biff that was used for writing this tutorial. Important bits (mainly, stuff you
should type) are highlighted in yellow:

```plain
$ %%bb -e "$(curl -s https://biffweb.com/new-project.clj)" tutorial%%
Enter name for project directory: %%eelchat%%
Enter main namespace (e.g. com.example): %%com.eelchat%%

Your project is ready. Run the following commands to get started:

  cd eelchat
  git init
  bb dev

And run `bb tasks` for a list of available commands.
```

You'll need to
[install Babashka](https://github.com/babashka/babashka#installation) if you
haven't already. After your project has been created, start up the app with
`bb dev`:

```plain
$ %%cd eelchat/%%

$ %%git init%%
Initialized empty Git repository in /home/jacob/dev/eelchat/.git/

$ %%git add .%%

$ %%git commit -m "First commit"%%
[master (root-commit) 95db1ca] First commit
 22 files changed, 785 insertions(+)
 [...]

$ %%bb dev%%
Downloading the latest version of Tailwind CSS...
[...]
[main] INFO com.biffweb.impl.util - %%System started.%%
[main] INFO com.eelchat - Go to http://localhost:8080
[chime-1] INFO com.eelchat.feat.worker - There are 0 users. (This message gets printed every 5 minutes. You can disable it by setting `:com.eelchat/enable-worker false` in config.edn)
nREPL server started on port 7888 on host localhost - nrepl://localhost:7888
```

Once you see that "System started" message above, the application is running.
Go to [localhost:8080](http://localhost:8080), then enter `hello@example.com`
into the signin form. You'll see a link printed to the terminal:

```plain
[qtp1015916495-34] INFO com.biffweb.impl.middleware -   2ms 200 get  /
[qtp1015916495-37] INFO com.biffweb.impl.middleware -   1ms 200 get  /css/main.css?t=1667015729284
[qtp1015916495-33] INFO com.biffweb.impl.middleware -   1ms 200 get  /img/glider.png
[qtp1015916495-37] INFO com.biffweb.impl.middleware -   2ms 200 get  /
Click here to sign in as hello@example.com: %%http://localhost:8080/auth/verify/ey[...]%%
[qtp1015916495-33] INFO com.biffweb.impl.middleware -  14ms 303 post /auth/send
[qtp1015916495-37] INFO com.biffweb.impl.middleware -   0ms 200 get  /auth/printed/
[qtp1015916495-33] INFO com.biffweb.impl.middleware -   0ms 200 get  /css/main.css?t=1667015729284
```

Open that link, and you should be signed in!

![A screenshot of the example app after signing in](/img/tutorial/signed-in.png)

### Delete some code

New projects come with a bunch of example code for you to inspect and tinker
with. But since this is a tutorial, we'll delete most of it and start fresh. First,
let's remove the `com.eelchat.feat.worker` namespace. Remove it from `com.eelchat` first:

```diff
;; src/com/eelchat.clj
;; ...
             [com.eelchat.feat.app :as app]
             [com.eelchat.feat.auth :as auth]
             [com.eelchat.feat.home :as home]
-            [com.eelchat.feat.worker :as worker]
             [com.eelchat.schema :refer [malli-opts]]
             [clojure.java.io :as io]
             [clojure.string :as str]
;; ...
 (def features
   [app/features
    auth/features
-   home/features
-   worker/features])
+   home/features])

 (def routes [["" {:middleware [anti-forgery/wrap-anti-forgery
                                biff/wrap-anti-forgery-websockets
```

Then delete the `src/com/eelchat/feat/worker.clj` file.

Next, we'll go to `com.eelchat.feat.app` and delete, well, almost everything.
This file is responsible for everything you see in the screenshot above. We'll replace it
with a simple `Nothing here yet` message:

```clojure
;; src/com/eelchat/feat/app.clj
(ns com.eelchat.feat.app
  (:require [com.biffweb :as biff :refer [q]]
            [com.eelchat.middleware :as mid]
            [com.eelchat.ui :as ui]
            [xtdb.api :as xt]))

(defn app [{:keys [session biff/db] :as req}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))]
    (ui/page
     {}
     nil
     [:div "Signed in as " email ". "
      (biff/form
       {:action "/auth/signout"
        :class "inline"}
       [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
        "Sign out"])
      "."]
     [:.h-6]
     [:div "Nothing here yet."])))

(def features
  {:routes ["/app" {:middleware [mid/wrap-signed-in]}
            ["" {:get app}]]})
```

Go back to [localhost:8080](http://localhost:8080), and it should look like
this after you refresh the page:

![A screenshot of the example app after updating app.clj](/img/tutorial/nothing-here-yet.png)

Finally, let's update our schema (`com.eelchat.schema`). We'll remove the
`:msg` document and the `:user/foo` and `:user/bar` attributes, all of which
were used by the code in `com.eelchat.feat.app` which we just deleted.

```diff
;; src/com/eelchat/schema.clj
;; ...
 (def schema
   {:user/id :uuid
    :user/email :string
-   :user/foo :string
-   :user/bar :string
    :user/joined-at inst?
    :user [:map {:closed true}
           [:xt/id :user/id]
           :user/email
-          :user/joined-at
-          [:user/foo {:optional true}]
-          [:user/bar {:optional true}]]
-
-   :msg/id :uuid
-   :msg/user :user/id
-   :msg/text :string
-   :msg/sent-at inst?
-   :msg [:map {:closed true}
-         [:xt/id :msg/id]
-         :msg/user
-         :msg/text
-         :msg/sent-at]})
+          :user/joined-at]})
```

We'll keep the `:user` document and the `:user/email` and `:user/joined-at` attributes, since these
are used in `com.eelchat.feat.auth` (to handle signing up/signing in).

If you played around with the example app at all, you might already have some
`:msg` documents or `:user/foo`/`:user/bar` attributes in your database. Let's
clear out the database to ensure we don't have any non-schema-conforming
documents. Hit `Ctrl-C` in your terminal to stop the app, then run
`rm -r storage/xtdb/`. Afterward, start the app up again with `bb dev`.
