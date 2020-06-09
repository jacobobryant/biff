(ns ^:biff hello.core
  (:require
    [biff.system]
    [clojure.tools.namespace.repl :as tn]
    [hello.handlers]
    [hello.routes]
    [hello.rules]
    [hello.static]
    [hello.triggers]))

(defn send-email [opts]
  (clojure.pprint/pprint [:send-email opts]))

(defn start-hello [sys]
  (tn/set-refresh-dirs "src" "../../src")
  (-> sys
    (merge #:hello.biff.auth{:send-email send-email
                             :on-signup "/signin/sent/"
                             :on-signin-request "/signin/sent/"
                             :on-signin-fail "/signin/fail/"
                             :on-signin "/app/"
                             :on-signout "/"})
    (merge #:hello.biff{:routes hello.routes/routes
                        :static-pages hello.static/pages
                        :event-handler #(hello.handlers/api % (:?data %))
                        :rules hello.rules/rules
                        :triggers hello.triggers/triggers})
    (biff.system/start-biff 'hello)))

(def components
  [{:name :hello/core
    :requires [:biff/init]
    :required-by [:biff/web-server]
    :start start-hello}])

(comment
  (crux.api/submit-tx (:hello.biff/node @biff.core/system)
    [[:crux.tx/delete {:game/id "test"}]
     [:crux.tx/delete {:game/id "testo"}] ]))
