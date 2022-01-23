(ns com.example.static
  (:require [com.example.views :as v]))

(def home
  (v/base
    {}
    nil
    [:p "hello there"]))

(def static-pages {"/" home})
