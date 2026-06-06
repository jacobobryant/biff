(ns com.example.components
  (:require [com.example.lib.config :as lib.config]
            [com.example.lib.ring :as lib.ring]))

(def components
  [lib.config/use-config
   lib.ring/use-webserver])
