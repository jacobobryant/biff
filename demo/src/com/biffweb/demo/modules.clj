(ns com.biffweb.demo.modules
  (:require [com.biffweb.background :as biff.background]
            [com.biffweb.datastar :as biff.datastar]
            [com.biffweb.demo.app.admin :as admin]
            [com.biffweb.demo.app.archive :as archive]
            [com.biffweb.demo.app.auth :as auth]
            [com.biffweb.demo.app.landing :as landing]
            [com.biffweb.demo.app.todos :as todos]
            [com.biffweb.demo.model.schema :as schema]
            [com.biffweb.demo.model.tab-state :as model.tab-state]
            [com.biffweb.demo.model.todo :as model.todo]
            [com.biffweb.demo.model.user :as model.user]
            [com.biffweb.fx :as biff.fx]
            [com.biffweb.graph :as biff.graph]
            [com.biffweb.ring :as biff.ring]
            [com.biffweb.sqlite :as biff.sqlite]))

(def modules
  [(biff.ring/module)
   (biff.datastar/module)
   (biff.background/module)
   (biff.fx/module)
   (biff.graph/module)
   (biff.sqlite/module)
   model.user/module
   model.tab-state/module
   model.todo/module
   schema/module
   admin/module
   landing/module
   auth/module
   archive/module
   todos/module])
