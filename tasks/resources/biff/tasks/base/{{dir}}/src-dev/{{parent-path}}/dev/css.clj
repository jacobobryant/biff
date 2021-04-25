(ns {{parent-ns}}.dev.css
  (:require
    [clojure.string :as str]
    [garden.core :as garden]
    [girouette.tw.core :refer [make-api]]
    [girouette.util :as util]
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

(def apply-classes
  '{btn ["text-center" "py-2" "px-4" "bg-dark" "text-white"
         "rounded" "disabled:opacity-50" "hover:bg-black"]
    input-text ["border" "border-gray-400" "rounded" "w-full" "py-2" "px-3" "leading-tight"
                "appearance-none" "focus:outline-none" "focus:ring" "focus:border-blue-300"
                "ring-blue-300" "text-black" "ring-opacity-30"]
    link ["text-blue-600" "hover:underline"]})

(def custom-components
  [{:id :max-w-prose
    :rules "
    max-w-prose = <'max-w-prose'>
           "
    :garden (fn [_]
              {:max-width "65ch"})}])

(def color-map
  {"dark" "343a40"})

(def components
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

(def class-name->garden
  (:class-name->garden
    (make-api components
      {:color-map (merge color/default-color-map color-map)
       :font-family-map typography/default-font-family-map})))

(comment
  (class-name->garden ""))
