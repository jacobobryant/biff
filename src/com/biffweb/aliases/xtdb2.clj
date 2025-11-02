(ns com.biffweb.aliases.xtdb2
  (:require [com.biffweb.impl.util :as util :refer [resolve-optional]]))

(def q                 (resolve-optional 'xtdb.api/q))
(def plan-q            (resolve-optional 'xtdb.api/plan-q))
(def start-node        (resolve-optional 'xtdb.node/start-node))
(def submit-tx         (resolve-optional 'xtdb.api/submit-tx))
(def ->normal-form-str (resolve-optional 'xtdb.util/->normal-form-str))
