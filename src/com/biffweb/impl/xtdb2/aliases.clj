(ns com.biffweb.impl.xtdb2.aliases
  (:require [com.biffweb.impl.util :as util :refer [resolve-optional]]))

(def execute-tx        (resolve-optional 'xtdb.api/execute-tx))
(def q                 (resolve-optional 'xtdb.api/q))
(def start-node        (resolve-optional 'xtdb.node/start-node))
(def submit-tx         (resolve-optional 'xtdb.api/submit-tx))
(def ->normal-form-str (resolve-optional 'xtdb.util/->normal-form-str))
