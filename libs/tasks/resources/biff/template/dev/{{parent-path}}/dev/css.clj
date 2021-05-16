(ns {{parent-ns}}.dev.css
  (:require [biff.dev.girouette :as gir]))

; See also:
; - https://github.com/green-coder/girouette
; - https://biff.findka.com/codox/biff.dev.girouette.html

(def applied-classes
  '{btn        ["bg-blue-500"
                "disabled:opacity-50"
                "hover:bg-blue-700"
                "px-4"
                "py-2"
                "rounded"
                "text-center"
                "text-white"]
    input-text ["appearance-none"
                "border"
                "border-gray-400"
                "focus:border-blue-300"
                "focus:outline-none"
                "focus:ring"
                "px-3"
                "py-2"
                "ring-blue-300"
                "ring-opacity-30"
                "rounded"
                "text-black"
                "w-full"]
    link       ["hover:underline"
                "text-blue-600"]})

(def color-map
  {"dark" "343a40"})

(def class-name->garden
  (gir/garden-fn {:color-map color-map}))

(defn write-css [opts]
  (gir/write-css
    (merge opts
           {:garden-fn class-name->garden
            :applied-classes applied-classes})))
