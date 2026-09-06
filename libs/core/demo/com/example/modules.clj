(ns com.example.modules
  (:require [com.example.app.landing-page :as landing-page]
            [com.example.lib.config :as lib.config]
            [com.example.lib.ring :as lib.ring]))

(def modules
  [lib.config/module
   landing-page/module
   lib.ring/module])
