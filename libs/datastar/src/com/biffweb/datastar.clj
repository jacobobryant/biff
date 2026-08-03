(ns com.biffweb.datastar
  (:require [com.biffweb.core :as biff.core]
            [com.biffweb.datastar.impl :as impl])
  (:import
   (java.util.concurrent.locks Condition ReentrantLock)))

(biff.core/register
 {:biff.datastar/buffer-size       :int
  :biff.datastar/condition         [:fn #(instance? Condition %)]
  :biff.datastar/epoch             [:fn #(instance? clojure.lang.IAtom %)]
  :biff.datastar/lock              [:fn #(instance? ReentrantLock %)]
  :biff.datastar/quality           :int
  :biff.datastar/rate-limit-ms     [:and :int pos?]
  :biff.datastar/signals           'map?
  :biff.datastar/sse-request       :boolean
  :biff.datastar/tab-id            :string
  :biff.datastar/window-size       :int})

(def init-opts
  impl/init-opts)

(defn new-lock []
  (impl/new-lock))

(defn refresh [ctx]
  (impl/refresh ctx))

(defn wrap-datastar [handler]
  (impl/wrap-datastar handler))

(defn module []
  (impl/module))

(defn signals-json [signals]
  (impl/signals-json signals))
