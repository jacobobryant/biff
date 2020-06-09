(ns ^:biff example.core
  (:require
    [biff.system]
    [clojure.tools.namespace.repl :as tn]
    [example.handlers]
    [example.routes]
    [example.rules]
    [example.static]
    [example.triggers]))

(defn send-email [opts]
  (clojure.pprint/pprint [:send-email opts]))

(defn start-example [sys]
  (tn/set-refresh-dirs "src" "../../src")
  (-> sys
    (merge #:example.biff.auth{:send-email send-email
                               :on-signup "/signin/sent/"
                               :on-signin-request "/signin/sent/"
                               :on-signin-fail "/signin/fail/"
                               :on-signin "/app/"
                               :on-signout "/"})
    (merge #:example.biff{:routes example.routes/routes
                          :static-pages example.static/pages
                          :event-handler #(example.handlers/api % (:?data %))
                          :rules example.rules/rules
                          :triggers example.triggers/triggers})
    (biff.system/start-biff 'example)))

(def components
  [{:name :example/core
    :requires [:biff/init]
    :required-by [:biff/web-server]
    :start start-example}])

(comment
  (crux.api/submit-tx (:example.biff/node @biff.core/system)
    [[:crux.tx/delete {:game/id "test"}]]))
