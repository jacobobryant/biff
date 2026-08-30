(ns com.biffweb.demo-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [com.biffweb.core :as biff.core]
            [com.biffweb.datastar :as biff.datastar]
            [com.biffweb.demo.lib.email :as email]
            [com.biffweb.demo.app.todos :as todos]
            [com.biffweb.demo.modules :as modules]
            [com.biffweb.demo.routes :as routes]
            [com.biffweb.sqlite :as biff.sqlite]
            [hato.client :as hato])
  (:import [java.net ServerSocket]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(def ^:dynamic *system* nil)
(def ^:dynamic *base-url* nil)
(def ^:dynamic *db-dir* nil)

(def test-start-order
  [:biff.sqlite/module
   :biff.admin/module
   :biff.background/module
   :biff.ring/module])

(def test-modules
  (atom (remove (comp #{:biff.config/module
                        :com.biffweb.demo/fake-pstats
                        :com.biffweb.demo/fake-errors}
                      :biff.core/id)
                modules/modules)))

(defn- free-port []
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

(defn- delete-tree! [path]
  (when path
    (doseq [file (reverse (file-seq (io/file path)))]
      (.delete ^java.io.File file))))

(defn- temp-db-dir []
  (str (Files/createTempDirectory
        "biff-demo-test"
        (into-array FileAttribute []))))

(defn- start-test-system [db-dir port]
  (biff.core/start
   {:biff.auth/skip-captcha  true
    :biff.ring/host          "127.0.0.1"
    :biff.ring/port          port
    :biff.ring/secure        false
    :biff.sqlite/db-path     (str db-dir "/main.db")
    :biff.sqlite/schema-path (str db-dir "/schema.sql")}
   test-modules
   test-start-order))

(defn- with-demo-system [f]
  (let [db-dir   (temp-db-dir)
        port     (free-port)
        system   (start-test-system db-dir port)
        base-url (str "http://127.0.0.1:" port)]
    (binding [*system*   system
              *base-url* base-url
              *db-dir*   db-dir]
      (try
        (f)
        (finally
          (biff.core/stop system)
          (delete-tree! db-dir))))))

(use-fixtures :each with-demo-system)

(defn- set-cookie->cookie [response]
  (some-> (get-in response [:headers "set-cookie"])
          (str/split #";" 2)
          first))

(defn- response-cookie [cookie response]
  (or (set-cookie->cookie response) cookie))

(defn- request-url [path]
  (str *base-url* path))

(defn- http-get [path & {:keys [cookie headers]}]
  (hato/get (request-url path)
            (cond-> {:as               :text
                     :headers          headers
                     :redirect-policy  :none
                     :throw-exceptions false}
              cookie
              (assoc-in [:headers "cookie"] cookie))))

(defn- http-post [path & {:keys [cookie headers form-params body]}]
  (hato/post (request-url path)
             (cond-> {:as               :text
                      :headers          {"sec-fetch-site" "same-origin"}
                      :redirect-policy  :none
                      :throw-exceptions false}
               cookie
               (assoc-in [:headers "cookie"] cookie)

               (seq headers)
               (update :headers merge headers)

               form-params
               (assoc :form-params form-params)

               body
               (assoc :body body))))

(defn- datastar-app-path [tab-id]
  (str "/app?u=&datastar="
       (java.net.URLEncoder/encode
        (biff.datastar/signals-json {:biff.datastar/client-tab-id tab-id})
        "UTF-8")))

(defn- datastar-get [path & {:keys [cookie]}]
  (http-get path
            :cookie cookie
            :headers {"datastar-request" "true"}))

(defn- datastar-post
  [path tab-id & {:keys [cookie headers form-params signals]}]
  (let [headers (merge {"datastar-request" "true"} headers)
        tab-id  {:biff.datastar/client-tab-id tab-id}]
    (if signals
      (http-post path
                 :cookie cookie
                 :headers (merge {"content-type" "application/json"} headers)
                 :body (biff.datastar/signals-json (merge tab-id signals)))
      (http-post path
                 :cookie cookie
                 :headers headers
                 :form-params (merge {(biff.datastar/signal-name
                                       :biff.datastar/client-tab-id)
                                      (:biff.datastar/client-tab-id tab-id)}
                                     form-params)))))

(defn- db-query [sql & params]
  (biff.sqlite/execute *system* (into [sql] params)))

(defn- wait-for! [f]
  (loop [attempts 40]
    (if-some [value (f)]
      value
      (if (pos? attempts)
        (do
          (Thread/sleep 50)
          (recur (dec attempts)))
        (throw (ex-info "Timed out waiting for condition." {}))))))

(defn- sign-in! []
  (let [email      (str "demo-test-" (random-uuid) "@example.com")
        sent-email (atom nil)]
    (with-redefs [email/send-email (fn [_ctx params]
                                     (reset! sent-email params)
                                     true)]
      (let [signin-page  (http-get "/signin")
            cookie       (response-cookie nil signin-page)
            send-code    (http-post "/_biff/auth/send-code"
                                    :cookie cookie
                                    :form-params {"email" email})
            _verify-page (http-get (get-in send-code [:headers "location"])
                                   :cookie cookie)
            verify-code  (http-post "/_biff/auth/verify-code"
                                    :cookie cookie
                                    :form-params {"email" email
                                                  "code"  (:code @sent-email)})
            cookie       (response-cookie cookie verify-code)
            app-page     (http-get "/app" :cookie cookie)]
        {:app-page    app-page
         :cookie      cookie
         :email       email
         :send-code   send-code
         :sent-email  @sent-email
         :verify-code verify-code}))))

(deftest modules-include-demo-fx-handlers-test
  (let [handlers (->> modules/modules
                      (keep :biff.fx/handlers)
                      (apply merge {}))]
    (is (contains? handlers :biff.background.fx/submit-jobs))
    (is (contains? handlers :biff.graph.fx/query))
    (is (contains? handlers :biff.sqlite.fx/execute))
    (is (contains? handlers :biff.sqlite.fx/authorized-write))))

(deftest landing-signin-and-admin-flow-test
  (let [home (http-get "/")

        {:keys [app-page cookie email send-code sent-email verify-code]}
        (sign-in!)

        users (db-query "SELECT id, email FROM user ORDER BY joined_at DESC")

        todos (db-query (str "SELECT archived FROM todo t JOIN user u "
                             "ON t.user_id = u.id WHERE u.email = ? "
                             "ORDER BY created_at ASC")
                        email)

        user-row          (some #(when (= email (or (:email %)
                                                    (:user/email %))) %)
                                users)
        admin-page        (http-get "/_biff/admin" :cookie cookie)
        signout           (http-post (routes/auth-signout) :cookie cookie)
        signed-out-cookie (response-cookie cookie signout)
        signed-out-app    (http-get "/app" :cookie signed-out-cookie)]
    (is (= 200 (:status home)))
    (is (str/includes? (:body home) "Biff Demo App"))
    (is (str/includes? (:body home) "/signin"))

    (is (= 303 (:status send-code)))
    (is (str/includes? (get-in send-code [:headers "location"]) "sent-to="))
    (is (= email (:to sent-email)))

    (is (= 303 (:status verify-code)))
    (is (= "/app" (get-in verify-code [:headers "location"])))
    (is (= 200 (:status app-page)))
    (is (str/includes? (:body app-page) email))
    (is (some? user-row))

    (is (= 5 (count todos)))
    (is (= 1 (count (filter :todo/archived todos))))
    (is (= 200 (:status admin-page)))
    (is (str/includes? (:body admin-page) "Admin Setup"))

    (is (= 303 (:status signout)))
    (is (= "/" (get-in signout [:headers "location"])))
    (is (= 303 (:status signed-out-app)))
    (is (= "/signin" (get-in signed-out-app [:headers "location"])))))

(deftest todo-mutations-work-over-http-test
  (let [{:keys [cookie] :as _signin} (sign-in!)
        title                        (str "Todo " (random-uuid))
        create-resp                  (http-post "/app/todos"
                                                :cookie cookie
                                                :form-params {"newtodo" title})
        todo-row                     (first (biff.sqlite/execute
                                             *system*
                                             {:select [:todo/id :todo/completed
                                                       :todo/archived]
                                              :from   :todo
                                              :where  [:= :todo/title title]}))
        toggle-resp                  (http-post (routes/todo-toggle (:todo/id todo-row))
                                                :cookie cookie
                                                :form-params
                                                {"completed" "true"})
        toggled-row                  (first (biff.sqlite/execute
                                             *system*
                                             {:select [:todo/completed]
                                              :from   :todo
                                              :where  [:= :todo/id
                                                       (:todo/id todo-row)]}))
        archive-resp                 (http-post (routes/todo-archive (:todo/id todo-row))
                                                :cookie cookie
                                                :form-params
                                                {"archived" "true"})
        archived-row                 (first (biff.sqlite/execute
                                             *system*
                                             {:select [:todo/archived]
                                              :from   :todo
                                              :where  [:= :todo/id
                                                       (:todo/id todo-row)]}))
        app-page                     (http-get "/app" :cookie cookie)]
    (is (= 200 (:status create-resp)))
    (is (str/includes? (:body create-resp) "datastar-patch-signals"))
    (is (some? todo-row))
    (is (false? (:todo/completed todo-row)))
    (is (false? (:todo/archived todo-row)))

    (is (= 204 (:status toggle-resp)))
    (is (true? (:todo/completed toggled-row)))

    (is (= 204 (:status archive-resp)))
    (is (true? (:todo/archived archived-row)))
    (is (not (str/includes? (:body app-page) title)))))

(deftest todo-page-renders-datastar-control-bindings-test
  (let [{:keys [app-page]} (sign-in!)
        body               (:body app-page)]
    (is (str/includes? body "$completed = el.checked;"))
    (is (str/includes? body "$archived = true;"))
    (is (str/includes? body "$filter = &quot;completed&quot;;"))
    (is (str/includes? body "$showArchived = el.checked;"))
    (is (str/includes? body "id=\"current-todos-section\""))
    (is (not (str/includes? body "id=\"archived-todos-section\"")))
    (is (not (str/includes? body "data-show=\"$showArchived\"")))
    (is (not (str/includes? body "data-signals:show-archived")))))

(deftest param-value-preserves-false-signal-values-test
  (is (false? (#'todos/param-value {:biff.datastar/signals
                                    {:showArchived false}}
                                   :show-archived)))
  (is (some? (#'todos/param-value {:biff.datastar/signals {:showArchived false}}
                                  :show-archived))))

(deftest datastar-todo-controls-work-over-http-test
  (let [{:keys [cookie]}   (sign-in!)
        tab-id             (random-uuid)
        title              (str "Datastar todo " (random-uuid))
        create-resp        (http-post "/app/todos"
                                      :cookie cookie
                                      :form-params {"newtodo" title})
        todo-row           (first (biff.sqlite/execute
                                   *system*
                                   {:select [:todo/id]
                                    :from   :todo
                                    :where  [:= :todo/title title]}))
        toggle-resp        (datastar-post
                            (routes/todo-toggle (:todo/id todo-row))
                            tab-id
                            :cookie cookie)
        toggle-row         (first (biff.sqlite/execute
                                   *system*
                                   {:select [:todo/completed]
                                    :from   :todo
                                    :where  [:= :todo/id (:todo/id todo-row)]}))
        filter-resp        (datastar-post (routes/tab-state)
                                          tab-id
                                          :cookie cookie
                                          :form-params {"filter" "completed"})
        filtered-page      (datastar-get (datastar-app-path tab-id)
                                         :cookie cookie)
        archive-resp       (datastar-post
                            (routes/todo-archive (:todo/id todo-row))
                            tab-id
                            :cookie cookie)
        archived-row       (first (biff.sqlite/execute
                                   *system*
                                   {:select [:todo/archived]
                                    :from   :todo
                                    :where  [:= :todo/id (:todo/id todo-row)]}))
        show-archived-resp (datastar-post (routes/tab-state)
                                          tab-id
                                          :cookie cookie
                                          :signals {"filter"       "all"
                                                    "showArchived" true})
        archived-page      (datastar-get (datastar-app-path tab-id)
                                         :cookie cookie)
        hide-archived-resp (datastar-post (routes/tab-state)
                                          tab-id
                                          :cookie cookie
                                          :signals {"filter"       "all"
                                                    "showArchived" false})
        hidden-page        (datastar-get (datastar-app-path tab-id)
                                         :cookie cookie)]
    (is (= 200 (:status create-resp)))
    (is (= 204 (:status toggle-resp)))
    (is (true? (:todo/completed toggle-row)))
    (is (= 204 (:status filter-resp)))
    (is (str/includes? (:body filtered-page) "Current filter: Completed"))
    (is (str/includes? (:body filtered-page) title))
    (is (= 204 (:status archive-resp)))
    (is (true? (:todo/archived archived-row)))
    (is (= 204 (:status show-archived-resp)))
    (is (str/includes? (:body archived-page) "Archived todos"))
    (is (str/includes? (:body archived-page) title))
    (is (= 204 (:status hide-archived-resp)))
    (is (not (str/includes? (:body hidden-page) title)))))

(deftest archive-queue-route-archives-active-todos-test
  (let [{:keys [cookie email]} (sign-in!)
        archive-resp           (http-post "/app/archive"
                                          :cookie cookie)
        active-count           (fn []
                                 (some-> (first (db-query
                                                 "SELECT COUNT(*) AS total
                                        FROM todo t
                                        JOIN user u ON t.user_id = u.id
                                        WHERE u.email = ? AND t.archived = 0"
                                                 email))
                                         :total
                                         (#(when (zero? %) %))))]
    (is (= 204 (:status archive-resp)))
    (is (zero? (wait-for! active-count)))))
