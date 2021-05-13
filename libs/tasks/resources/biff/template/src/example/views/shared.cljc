(ns {{parent-ns}}.views.shared
  (:require [rum.core :refer [defc react reactive]]))

(defc header < reactive
  [{:keys [email]}]
  [:.flex
   [:div "Signed in as " (react email)]
   [:.flex-grow]
   [:a.text-blue-500.hover:text-blue-800 {:href "/api/signout"}
    "Sign out"]])

(defc tabs < reactive
  [{:keys [tab-data active-id]}]
  [:ul.flex.border-b
   (for [{:keys [id href label]} tab-data]
     (if (= (react active-id) id)
       [:li.-mb-px.mr-1 {:key (str id)}
        [:a {:class ["bg-white"
                     "inline-block"
                     "border-l"
                     "border-t"
                     "border-r"
                     "rounded-t"
                     "py-2"
                     "px-4"
                     "font-semibold"]
             :href href}
         label]]
       [:li.mr-1 {:key (str id)}
        [:a {:class ["bg-white"
                     "inline-block"
                     "py-2"
                     "px-4"
                     "text-blue-500"
                     "hover:text-blue-800"
                     "font-semibold"]
             :href href}
         label]]))])
