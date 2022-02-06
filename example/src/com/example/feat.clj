(ns com.example.feat
  (:require [com.biffweb :as biff]
            [com.example.feat.app :as app]
            [com.example.feat.auth :as auth]
            [com.example.feat.home :as home]
            [com.example.feat.misc :as misc]))

(def features
  [app/features
   auth/features
   home/features
   misc/features])

(def routes (map :routes features))
(def static-pages (apply biff/safe-merge (map :static features)))
