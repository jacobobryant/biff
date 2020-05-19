(ns hello.routes)

(defn echo [{:keys [params] :as req}]
  (clojure.pprint/pprint (sort (keys req)))
  {:status 200
   ; Biff's middleware changes :headers/* and :cookies/* to nested maps.
   :headers/Content-Type "application/edn"
   :body (prn-str params)})

; See Reitit
(def routes
  [["/echo" {:get echo
             :post echo
             :name ::echo}]])
