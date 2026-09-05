(ns com.biffweb.tasks.impl.init-test
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [com.biffweb.tasks.impl.init :as init]
            [com.biffweb.tasks.impl.update :as tasks-update]
            [com.biffweb.tasks.impl.util :as util]))

(deftest git-initialization-confirmation
  (doseq [answer   ["\n" "y\n" "YES\n" "n\n" ""]
          metadata [:directory :file]]
    (let [root     (fs/create-temp-dir)
          git-path (io/file (str root) ".git")
          calls    (atom [])
          accept?  (contains? #{"\n" "y\n" "YES\n"} answer)]
      (try
        (if (= metadata :directory)
          (do
            (fs/create-dir git-path)
            (spit (io/file git-path "config") "original"))
          (spit git-path "gitdir: elsewhere"))
        (with-redefs [util/project-root  (constantly (io/file (str root)))
                      util/shell-inherit (fn [& args]
                                           (is (not (fs/exists? git-path)))
                                           (swap! calls conj (vec args)))]
          (let [output (with-in-str answer
                         (with-out-str
                           (#'init/initialize-git-repository!)))]
            (is (re-find #"\(Y/n\)" output))))
        (is (= (not accept?) (fs/exists? git-path)))
        (is (= (if accept?
                 (mapv #(into ["git" "-C" (str root)] %)
                       [["init"] ["add" "."] ["commit" "-m" "First commit"]])
                 [])
               @calls))
        (finally
          (fs/delete-tree root))))))

(deftest git-initialization-when-namespace-needs-init
  (doseq [[needs-rename? renamed?] [[false false] [true false] [true true]]]
    (let [calls (atom [])]
      (with-redefs-fn
        {#'util/read-config                     (constantly {:biff.tasks/main-ns 'com.example})
         #'init/needs-main-namespace-init?      (constantly needs-rename?)
         #'init/rewrite-main-namespace!         (fn []
                                                  (swap! calls conj :rename)
                                                  renamed?)
         #'init/ensure-config-files             #(swap! calls conj :config)
         #'init/ensure-task-binaries-installed! (fn [_] (swap! calls conj :binaries))
         #'tasks-update/update                  (fn [& _] (swap! calls conj :update))
         #'init/initialize-git-repository!      #(swap! calls conj :git)}
        init/init)
      (is (= (cond-> []
               needs-rename? (conj :rename)
               true (into [:config :binaries :update])
               needs-rename? (conj :git))
             @calls)))))
