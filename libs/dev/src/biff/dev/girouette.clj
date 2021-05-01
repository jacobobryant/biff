(ns biff.dev.girouette
  (:require [cljs.analyzer.api :as ana-api]
            [cljs.closure :as closure]
            [cljs.compiler.api :as compiler]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [garden.core :as garden]
            [girouette.tw.core :refer [make-api]]
            [girouette.util :as util]
            [girouette.tw.preflight :refer [preflight]]
            [girouette.tw.common :as common]
            [girouette.tw.color :as color]
            [girouette.tw.layout :as layout]
            [girouette.tw.flexbox :as flexbox]
            [girouette.tw.grid :as grid]
            [girouette.tw.box-alignment :as box-alignment]
            [girouette.tw.spacing :as spacing]
            [girouette.tw.sizing :as sizing]
            [girouette.tw.typography :as typography]
            [girouette.tw.background :as background]
            [girouette.tw.border :as border]
            [girouette.tw.effect :as effect]
            [girouette.tw.table :as table]
            [girouette.tw.animation :as animation]
            [girouette.tw.transform :as transform]
            [girouette.tw.interactivity :as interactivity]
            [girouette.tw.svg :as svg]
            [girouette.tw.accessibility :as accessibility]))

(def ^:private custom-components
  [{:id :max-w-prose
    :rules "
    max-w-prose = <'max-w-prose'>
           "
    :garden (fn [_]
              {:max-width "65ch"})}])

(def ^:private components*
  (util/into-one-vector
    [accessibility/components
     animation/components
     background/components
     border/components
     box-alignment/components
     common/components
     effect/components
     flexbox/components
     grid/components
     interactivity/components
     layout/components
     sizing/components
     spacing/components
     svg/components
     table/components
     transform/components
     typography/components
     custom-components]))

(defn- sym->cls [sym]
  (str/replace (str "." sym) ":" "\\:"))

(defn- add-applied-classes [classes garden]
  (concat garden
    (for [[from to] classes
          :let [to (set (map sym->cls to))
                garden-vals (->> garden
                              (filter vector?)
                              (filter (fn [[cls value]]
                                        (contains? to cls)))
                              (map second))
                map-vals (->> garden-vals
                           (filter map?)
                           (reduce merge))
                non-map-vals (remove map? garden-vals)]]
      (apply vector
        (sym->cls from) map-vals non-map-vals))))

(defn- keyword-hook [hook-fn]
  (fn [env ast opts]
    (when (and (= (:op ast) :const)
               (= (:tag ast) 'cljs.core/Keyword))
      (hook-fn (-> ast :val)))
    ast))

(defn- string-hook [hook-fn]
  (fn [env ast opts]
    (when (and (= (:op ast) :const)
               (= (:tag ast) 'string))
      (hook-fn (-> ast :val)))
    ast))

(defn- gather-css-classes [state ^java.util.File file]
  (let [css-classes (atom #{})
        passes [(string-hook (fn [s]
                               (let [names (->> (str/split s #" ")
                                                (remove str/blank?))]
                                 (swap! css-classes into names))))
                (keyword-hook (fn [kw]
                                (let [names (->> (name kw)
                                                 (re-seq #"\.[^\.#]+")
                                                 (map (fn [s] (subs s 1))))]
                                  (swap! css-classes into names))))]]
    (try
      (ana-api/no-warn
        (ana-api/with-passes passes
          (ana-api/analyze-file state file nil)))
      (catch Exception e
        (println "Couldn't parse" (.getPath file))))
    @css-classes))

(defn- new-state []
  (let [deps (closure/get-upstream-deps)
        npm-deps (when (map? (:npm-deps deps))
                   (keys (:npm-deps deps)))
        foreign-libs (mapcat :provides (:foreign-libs deps))
        stubbed-js-deps (zipmap (concat npm-deps foreign-libs)
                                (repeatedly #(gensym "fake$module")))
        state (ana-api/empty-state)]
    (ana-api/with-state state (compiler/with-core-cljs))
    (swap! state update :js-dependency-index #(merge stubbed-js-deps %))))

(defn write-css [{:keys [output-file
                         verbose
                         paths
                         exts
                         garden-fn
                         applied-classes]
                  :or {verbose true
                       exts [".clj" ".cljc" ".cljs"]
                       paths ["src"]}}]
  (io/make-parents output-file)
  (let [state (new-state)
        classes (->> paths
                     (mapcat #(file-seq (io/file %)))
                     (filter (fn [^java.io.File f]
                               (some #(str/ends-with? (.getPath f) %) exts)))
                     (mapcat #(gather-css-classes state %))
                     sort
                     dedupe
                     (filter garden-fn))]
    (when verbose
      (println classes))
    (->> classes
         (map garden-fn)
         (into preflight)
         (add-applied-classes applied-classes)
         garden/css
         (spit output-file))))

(defn garden-fn [{:keys [components color-map font-family-map]}]
  (:class-name->garden
    (make-api (into components* components)
              {:color-map (merge color/default-color-map color-map)
               :font-family-map (merge typography/default-font-family-map
                                       font-family-map)})))
