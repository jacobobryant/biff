(ns com.example.feat.home
  (:require [com.example.views :as v]))

(def home
  (v/base
    {}
    nil
    [:p.link "hello there"]))

(def features
  {:static {"/" home}})
