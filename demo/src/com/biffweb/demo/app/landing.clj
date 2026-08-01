(ns com.biffweb.demo.app.landing
  (:require [com.biffweb.demo.lib.ui :as ui]
            [com.biffweb.demo.routes :as routes]))

(defn home
  [{:keys [session]}]
  (if (:uid session)
    {:status  303
     :headers {"location" (routes/app)}}
    (ui/page
     {:title "Biff Demo App"}
     [:section.space-y-6
      [:p {:class "text-sm font-semibold uppercase text-teal-700 tracking-[0.2em]"} "Biff demo"]
      (ui/page-title "A showcase app for Biff itself")
      [:p.max-w-3xl.text-lg.text-slate-600
       "Sign in to a TodoMVC-style app that exercises Biff authentication, Datastar live updates, SQLite-backed ownership, graph resolvers, fx machines, background jobs, and the admin dashboard."]
      [:div.grid.gap-4.rounded-3xl.border.border-slate-200.bg-white.p-6.shadow-sm
       [:h2.text-lg.font-semibold.text-slate-950 "What to try"]
       [:ul.grid.gap-3.text-slate-600
        [:li "Open the app in two tabs and watch Datastar keep both views in sync."]
        [:li "Create and complete todos to exercise graph reads and authorized writes."]
        [:li "Use the archive button to queue background work in batches of three."]
        [:li "Open the admin dashboard to inspect users and activity events."]]]
      [:div.flex.flex-wrap.items-center.gap-3
       (ui/link {:href  (routes/signin)
                 :class "rounded-2xl bg-teal-600 px-4 py-3 text-white no-underline hover:bg-teal-700 hover:text-white"}
                "Sign in to the demo")]])))

(def module
  {:biff.ring/routes
   [(routes/home) {:get  home
                   :name ::home}]})
