(ns {{parent-ns}}.static)

; See https://findka.com/biff/#static-resources
; and https://github.com/tonsky/rum

(defn base-page [{:keys [scripts] :as opts} & contents]
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:link {:rel "stylesheet" :href "/css/main.css"}]]
   [:body
    [:.p-3.mx-auto.max-w-screen-sm
     contents]
    (for [url scripts]
      [:script {:src url}])]])

(def signin-form
  (list
    [:.text-lg "Email address:"]
    [:.h-3]
    [:form.mb-0 {:action "/api/signin-request" :method "post"}
     [:.flex
      [:input.border.border-gray-500.rounded.p-2
       {:name "email" :type "email" :placeholder "Email"
        :value "abc@example.com"}]
      [:.w-3]
      ; Same as [:button.px-4.py-2.rounded.bg-blue-500.text-white.hover:bg-blue-700 ...]
      ; See tailwind.css
      [:button.btn {:type "submit"} "Sign in"]]]
    [:.h-1]
    [:.text-sm "Doesn't need to be a real address."]))

(def home
  (base-page {:scripts ["/js/ensure-signed-out.js"]}
    signin-form))

(def signin-sent
  (base-page {}
    [:p "Sign-in link sent, please check your inbox."]
    [:p.text-sm "(Just kidding: click on the sign-in link that was printed to the console.)"]))

(def signin-fail
  (base-page {}
    [:p "Invalid sign-in token."]
    signin-form))

(def not-found
  (base-page {}
    [:p "Not found."]))

; Biff adds index.html to paths that end in /.
(def pages
  {"/" home
   ; Same as "/signin-sent/index.html" signin-sent
   "/signin-sent/" signin-sent
   "/signin-fail/" signin-fail
   "/404.html" not-found})

; To update static files during development, delete the #_ and then eval this
; namespace. (Don't forget to put the #_ back when you're done).
#_(do
    (biff.components/write-static-resources
      (assoc @biff.core/system :biff/static-pages pages))
    nil)
