(ns com.biffweb.demo.routes
  (:require [com.biffweb.ring :refer [defpath]]))

(defpath home "/")
(defpath app "/app")
(defpath signin "/signin")
(defpath auth-signout "/_biff/auth/signout")
(defpath todo-create "/app/todos")
(defpath todo-archive-batch "/app/archive")
(defpath todo-archive "/app/todos/:id/archive")
(defpath todo-toggle "/app/todos/:id/toggle")
(defpath tab-state "/app/tab-state")
