(ns ^:biff biff.console
  (:require
    [biff.system :refer [start-biff]]))

(def console
  {:name :biff/console
   :requires [:biff/core]
   :required-by [:biff/web-server]
   :start #(-> %
             (update :console.biff.auth/send-email requiring-resolve)
             (merge #:console.biff.auth{:on-signin "/"
                                        :on-signin-request "/biff/signin-request"
                                        :on-signin-fail "/biff/signin-fail"
                                        :on-signout "/biff/signin"})
             (start-biff 'console.biff))})

(def components [console])
