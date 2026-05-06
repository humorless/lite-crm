(ns lite-crm.views
  (:require [manifest-edn.core :as manifest]
            [lite-crm.routes :as-alias routes]
            [reitit-extras.core :as ext]))

(defn base
  "Base component for html page."
  [content]
  [:html
   {:lang "en"}
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=0"}]
    [:meta {:name "msapplication-TileColor"
            :content "#ffffff"}]
    [:link {:rel "manifest"
            :href "/assets/manifest.json"}]
    [:link {:rel "icon"
            :href (manifest/asset "images/icon@32px.png")}]
    [:link {:rel "icon"
            :href (manifest/asset "images/icon.svg")
            :type "image/svg+xml"}]
    [:link {:rel "apple-touch-icon"
            :sizes "180x180"
            :href (manifest/asset "images/icon@180px.png")}]
    [:link {:type "text/css"
            :href (manifest/asset "css/output.css")
            :rel "stylesheet"}]
    [:title "Clojure Stack Lite | A Template for Clojure Projects"]]
   [:body
    content
    [:script {:type "text/javascript"
              :src (manifest/asset "js/htmx.min.js")
              :defer true}]
    [:script {:type "text/javascript"
              :src (manifest/asset "js/alpinejs.min.js")
              :defer true}]]])

(defn error-page
  [text]
  (base
    [:div {:class ["mt-56"]}
     [:div {:class ["mx-auto" "text-center"]}
      [:h1 {:class ["text-5xl"]} text]]]))

(defn button
  [{:keys [url text props]}]
  [:a (merge {:class ["bg-white dark:bg-slate-800 hover:bg-slate-100 dark:hover:bg-slate-700"
                      "text-slate-900 dark:text-white font-medium py-2 px-4 rounded-lg "
                      "border border-slate-300 dark:border-slate-600 transition-colors duration-200"]
              :href url}
             props)
   text])

(defn nav
  "Top navigation bar for authenticated pages."
  [{:keys [user router]}]
  [:nav {:class ["bg-white" "border-b" "border-gray-200" "sticky" "top-0" "z-10"]}
   [:div {:class ["container" "mx-auto" "px-4" "max-w-6xl" "flex" "items-center"
                  "justify-between" "h-14"]}
    [:div {:class ["flex" "gap-6"]}
     [:a {:class ["font-semibold" "text-gray-800" "hover:text-indigo-600" "text-sm"]
          :href (ext/get-route router ::routes/home)} "總覽"]
     [:a {:class ["text-gray-600" "hover:text-indigo-600" "text-sm"]
          :href (ext/get-route router ::routes/companies)} "公司"]
     [:a {:class ["text-gray-600" "hover:text-indigo-600" "text-sm"]
          :href (ext/get-route router ::routes/contacts)} "聯絡人"]
     [:a {:class ["text-gray-600" "hover:text-indigo-600" "text-sm"]
          :href (ext/get-route router ::routes/logs)} "聯絡記錄"]]
    [:div {:class ["flex" "items-center" "gap-4"]}
     [:span {:class ["text-xs" "text-gray-400"]} (:email user)]
     [:button {:class ["text-sm" "text-gray-500" "hover:text-red-500" "cursor-pointer"]
               :hx-post (ext/get-route router ::routes/logout)
               :hx-headers (ext/csrf-token-json)} "登出"]]]])

(defn layout
  "Main layout with nav. Use for all authenticated CRM pages."
  [context content]
  (base
    [:div {:class ["min-h-screen" "bg-gray-50"]}
     (nav context)
     [:main {:class ["container" "mx-auto" "px-4" "py-6" "max-w-6xl"]}
      content]]))

(defn- reminder-urgency-badge [reminder-date]
  (let [today    (.toString (java.time.LocalDate/now))
        overdue? (neg? (compare reminder-date today))]
    (if overdue?
      [:span {:class ["inline-flex" "items-center" "px-2" "py-0.5" "rounded" "text-xs"
                      "font-medium" "bg-red-100" "text-red-700"]} "逾期"]
      [:span {:class ["inline-flex" "items-center" "px-2" "py-0.5" "rounded" "text-xs"
                      "font-medium" "bg-yellow-100" "text-yellow-700"]} "即將到期"])))

(defn home-page
  [{:keys [reminders recent-logs] :as data}]
  (layout data
    [:div
     [:h1 {:class ["text-2xl" "font-bold" "text-gray-800" "mb-6"]} "儀表板"]
     [:div {:class ["grid" "grid-cols-2" "gap-6"]}
      [:div {:class ["bg-white" "rounded-lg" "shadow" "p-6"]}
       [:h2 {:class ["text-sm" "font-semibold" "text-gray-600" "mb-4" "uppercase"
                     "tracking-wide"]} "提醒事項（30 天內）"]
       (if (seq reminders)
         [:ul {:class ["space-y-3"]}
          (for [r reminders]
            [:li {:class ["flex" "items-start" "gap-3" "border-t" "border-gray-100" "pt-3"]}
             [:div {:class ["flex-1"]}
              [:div {:class ["flex" "items-center" "gap-2" "mb-1"]}
               (reminder-urgency-badge (:reminder-date r))
               [:span {:class ["text-xs" "text-gray-400"]} (:reminder-date r)]]
              [:p {:class ["text-sm" "font-medium" "text-gray-800"]}
               (if (= "company" (:entity-type r))
                 [:a {:class ["hover:text-indigo-600"]
                      :href (str "/companies/" (:entity-id r))}
                  (:company-name r)]
                 [:a {:class ["hover:text-indigo-600"]
                      :href (str "/contacts/" (:entity-id r))}
                  (:contact-name r)])]
              [:div {:class ["flex" "items-center" "gap-2"]}
               [:span {:class ["bg-indigo-50" "text-indigo-700" "px-2" "py-0.5" "rounded-full"
                               "text-xs"]} (:name r)]
               (when (:notes r)
                 [:span {:class ["text-xs" "text-gray-400"]} (:notes r)])]]])]
         [:p {:class ["text-sm" "text-gray-400" "text-center" "py-4"]} "目前無提醒"])]
      [:div {:class ["bg-white" "rounded-lg" "shadow" "p-6"]}
       [:h2 {:class ["text-sm" "font-semibold" "text-gray-600" "mb-4" "uppercase"
                     "tracking-wide"]} "最近聯絡記錄"]
       (if (seq recent-logs)
         [:ul {:class ["space-y-3"]}
          (for [log recent-logs]
            [:li {:class ["border-t" "border-gray-100" "pt-3"]}
             [:div {:class ["flex" "items-center" "gap-2" "mb-1"]}
              [:a {:class ["text-sm" "font-medium" "text-indigo-600" "hover:underline"]
                   :href (str "/companies/" (:company-id log))}
               (:company-name log)]
              [:span {:class ["text-xs" "text-gray-400"]} (:date log)]
              (when (:contact-names log)
                [:span {:class ["text-xs" "text-gray-500"]} (:contact-names log)])]
             [:p {:class ["text-sm" "text-gray-700"]}
              (let [c (:content log)]
                (if (> (count c) 80) (str (subs c 0 80) "…") c))]])]
         [:p {:class ["text-sm" "text-gray-400" "text-center" "py-4"]} "尚無記錄"])]]]
   ))
