---
title: Start a new project
---

[View the code for this section](https://github.com/jacobobryant/eelchat/commit/36cbd960304f2e0e6448ff9fcd989da5f1c76454).

As with any Biff project, we'll need to run the new project script. We'll add a
`tutorial` argument so that your project will be based on the same version of
Biff that was used for writing this tutorial. Important bits (mainly, stuff you
should type) are highlighted in yellow:

```plain
$ %%clj -M -e '(load-string (slurp "https://biffweb.com/new.clj"))' -M tutorial%%
Enter name for project directory: %%eelchat%%
Enter main namespace (e.g. com.example): %%com.eelchat%%

Your project is ready. Run the following commands to get started:

  cd eelchat
  git init
  git add .
  git commit -m "First commit"
  clj -M:dev dev

Run `clj -M:dev --help` for a list of available commands.
```

You'll need to
[install clj](https://clojure.org/guides/install_clojure) if you
haven't already. After your project has been created, start up the app with
`clj -M:dev dev`:

```plain
$ %%cd eelchat/%%

$ %%git init%%
Initialized empty Git repository in /home/jacob/dev/eelchat/.git/

$ %%git add .%%

$ %%git commit -m "First commit"%%
 [master (root-commit) 85f99db] First commit
 26 files changed, 1146 insertions(+)
 [...]

$ %%clj -M:dev dev%%
New config generated and written to config.env.
[main] INFO hello.there - starting: com.biffweb$use_aero_config@1449ffba
[...]
[main] INFO hello.there - %%System started.%%
[main] INFO hello.there - Go to http://localhost:8080
[chime-1] INFO hello.there.worker - There are 0 users.
nREPL server started on port 7888 on host localhost - nrepl://localhost:7888
```

Once you see that "System started" message above, the application is running.
Go to [localhost:8080](http://localhost:8080), then enter `hello@example.com`
into the signin form. You'll see a link printed to the terminal:

```plain
[qtp1209549907-42] INFO com.biffweb.impl.middleware -  10ms 200 get  /
[qtp1209549907-41] INFO com.biffweb.impl.middleware -   1ms 200 get  /css/main.css?t=1707102698000
[qtp1209549907-44] INFO com.biffweb.impl.middleware -   0ms 200 get  /js/main.js?t=1707102552000
TO: hello@example.com
SUBJECT: Sign up for My Application

We received a request to sign up to My Application using this email address. Click this link to sign up:

%%http://localhost:8080/auth/verify-link/ey[...]%%

This link will expire in one hour. If you did not request this link, you can ignore this email.

To send emails instead of printing them to the console, add your API keys for MailerSend and Recaptcha to config.edn.
[qtp1209549907-42] INFO com.biffweb.impl.middleware -  10ms 303 post /auth/send-link
[qtp1209549907-44] INFO com.biffweb.impl.middleware -   4ms 200 get  /link-sent?email=hello@example.com
[qtp1209549907-42] INFO com.biffweb.impl.middleware -   1ms 200 get  /css/main.css?t=1707102698000
```

Open that link, and you should be signed in!

![A screenshot of the starter app after signing in](/img/tutorial/signed-in.png)

### Delete some code

New projects come with a bunch of example code for you to inspect and tinker
with. But since this is a tutorial, we'll delete most of it and start fresh. First,
let's remove the `com.eelchat.worker` namespace. Remove it from `com.eelchat` first:

```diff
;; src/com/eelchat.clj
;; ...
             [com.eelchat.home :as home]
             [com.eelchat.middleware :as mid]
             [com.eelchat.ui :as ui]
-            [com.eelchat.worker :as worker]
             [com.eelchat.schema :as schema]
             [clojure.test :as test]
             [clojure.tools.logging :as log]
;; ...
   [app/module
    (biff/authentication-module {})
    home/module
-   schema/module
-   worker/module])
+   schema/module])
 
 (def routes [["" {:middleware [mid/wrap-site-defaults]}
               (keep :routes modules)]
```

Then delete the `src/com/eelchat/worker.clj` file.

Next, we'll go to `com.eelchat.app` and delete, well, almost everything.
This file is responsible for everything you see in the screenshot above. We'll replace it
with a simple `Nothing here yet` message:

```clojure
;; src/com/eelchat/app.clj
(ns com.eelchat.app
  (:require [com.biffweb :as biff :refer [q]]
            [com.eelchat.middleware :as mid]
            [com.eelchat.ui :as ui]
            [xtdb.api :as xt]))

(defn app [{:keys [session biff/db] :as ctx}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))]
    (ui/page
     {}
     [:div "Signed in as " email ". "
      (biff/form
       {:action "/auth/signout"
        :class "inline"}
       [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
        "Sign out"])
      "."]
     [:.h-6]
     [:div "Nothing here yet."])))

(def module
  {:routes ["/app" {:middleware [mid/wrap-signed-in]}
            ["" {:get app}]]})
```

Go back to [localhost:8080](http://localhost:8080), and it should look like
this after you refresh the page:

![A screenshot of the starter app after updating app.clj](/img/tutorial/nothing-here-yet.png)

Finally, let's update our schema (`com.eelchat.schema`). We'll remove the
`:msg` document and the `:user/foo` and `:user/bar` attributes, all of which
were used by the code in `com.eelchat.app` which we just deleted.

```diff
;; src/com/eelchat/schema.clj
;; ...
 (def schema
   {:user/id :uuid
    :user [:map {:closed true}
           [:xt/id                     :user/id]
           [:user/email                :string]
+          [:user/joined-at            inst?]]})
-          [:user/joined-at            inst?]
-          [:user/foo {:optional true} :string]
-          [:user/bar {:optional true} :string]]
-
-   :msg/id :uuid
-   :msg [:map {:closed true}
-         [:xt/id       :msg/id]
-         [:msg/user    :user/id]
-         [:msg/text    :string]
-         [:msg/sent-at inst?]]})
```

If you played around with the starter app at all, you might already have some
`:msg` documents or `:user/foo`/`:user/bar` attributes in your database. Let's
clear out the database to ensure we don't have any non-schema-conforming
documents. Hit `Ctrl-C` in your terminal to stop the app, then run
`rm -r storage/xtdb/`. Afterward, start the app up again with `clj -M:dev dev`.
