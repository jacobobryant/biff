(ns com.example.modules
  (:require [com.example.app.landing-page :as landing-page]
            [com.example.lib.ring :as lib.ring]))

(def modules
  [landing-page/module
   lib.ring/module])
