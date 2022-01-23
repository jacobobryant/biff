(ns com.example.feat
  (:require [com.biffweb :as biff]
            [com.example.feat.home :as home]
            [com.example.feat.misc :as misc]))

(def features
  [home/features
   misc/features])

(def routes (mapcat :routes features))

(def static-pages (apply biff/safe-merge (map :static features)))
