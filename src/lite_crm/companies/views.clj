(ns lite-crm.companies.views
  "Hiccup views for companies list and create."
  (:require [lite-crm.routes :as-alias routes]
            [lite-crm.views :as base]
            [reitit-extras.core :as ext]))

(def ^:private tier-labels
  {"has_plan"  "有規劃"
   "has_need"  "有需求"
   "no_plan"   "沒規劃"
   "abandoned" "放棄"})

(def ^:private tier-colors
  {"has_plan"  "bg-green-100 text-green-800"
   "has_need"  "bg-blue-100 text-blue-800"
   "no_plan"   "bg-yellow-100 text-yellow-800"
   "abandoned" "bg-red-100 text-red-800"})

(defn tier-badge
  "Colored badge for a tier value."
  [tier]
  [:span {:class ["inline-flex" "items-center" "px-2" "py-0.5" "rounded" "text-xs"
                  "font-medium" (get tier-colors tier "bg-gray-100 text-gray-800")]}
   (get tier-labels tier tier)])

(defn companies-list
  "Companies table with filter bar."
  [{:keys [companies router filters]}]
  [:div
   [:div {:class ["flex" "justify-between" "items-center" "mb-4"]}
    [:h1 {:class ["text-2xl" "font-bold" "text-gray-800"]} "公司列表"]
    [:a {:class ["bg-indigo-600" "text-white" "px-4" "py-2" "rounded-lg"
                 "hover:bg-indigo-700" "text-sm" "font-medium"]
         :href (ext/get-route router ::routes/new-company)} "+ 新增公司"]]
   [:form {:class ["flex" "gap-3" "mb-4"] :method "get"
           :action (ext/get-route router ::routes/companies)}
    [:select {:class ["border" "border-gray-300" "rounded-lg" "px-3" "py-2" "text-sm"
                      "bg-white"] :name "tier"}
     [:option {:value "" :selected (nil? (:tier filters))} "所有等級"]
     (for [[tier-val label] tier-labels]
       [:option {:value tier-val :selected (= tier-val (:tier filters))} label])]
    [:input {:class ["border" "border-gray-300" "rounded-lg" "px-3" "py-2" "text-sm" "flex-1"]
             :type "text" :name "company-name" :placeholder "公司名稱搜尋"
             :value (:company-name filters "")}]
    [:input {:class ["border" "border-gray-300" "rounded-lg" "px-3" "py-2" "text-sm" "flex-1"]
             :type "text" :name "tag-name" :placeholder "興趣標籤"
             :value (:tag-name filters "")}]
    [:button {:class ["bg-gray-100" "text-gray-700" "px-4" "py-2" "rounded-lg"
                      "text-sm" "hover:bg-gray-200"] :type "submit"} "篩選"]]
   [:div {:class ["bg-white" "rounded-lg" "shadow-sm" "overflow-hidden"]}
    [:table {:class ["w-full" "text-sm"]}
     [:thead
      [:tr {:class ["bg-gray-50" "text-gray-500" "text-left" "text-xs" "uppercase"
                    "tracking-wider"]}
       [:th {:class ["px-4" "py-3" "font-medium"]} "公司名稱"]
       [:th {:class ["px-4" "py-3" "font-medium"]} "產業"]
       [:th {:class ["px-4" "py-3" "font-medium"]} "等級"]
       [:th {:class ["px-4" "py-3" "font-medium"]} "最近聯絡"]]]
     [:tbody
      (if (seq companies)
        (for [c companies]
          [:tr {:class ["border-t" "border-gray-100" "hover:bg-gray-50"]}
           [:td {:class ["px-4" "py-3"]}
            [:a {:class ["text-indigo-600" "hover:underline" "font-medium"]
                 :href (str (ext/get-route router ::routes/companies) "/" (:id c))}
             (:name c)]]
           [:td {:class ["px-4" "py-3" "text-gray-600"]} (or (:industry c) "—")]
           [:td {:class ["px-4" "py-3"]} (tier-badge (:tier c))]
           [:td {:class ["px-4" "py-3" "text-gray-500"]}
            (or (:last-log-date c) "—")]])
        [:tr [:td {:class ["px-4" "py-8" "text-center" "text-gray-400"]
                   :colspan 4} "尚無公司資料"]])]]]])

(defn companies-page
  "Full page: companies list."
  [data]
  (base/layout data (companies-list data)))

(defn new-company-form
  "Create-company form (also used as HTMX partial for validation errors)."
  [{:keys [router values errors]}]
  [:form {:id "new-company-form"
          :class ["bg-white" "rounded-lg" "shadow-sm" "p-6" "space-y-4" "max-w-lg"]
          :hx-post (ext/get-route router ::routes/companies)
          :hx-target "#new-company-form"
          :hx-swap "outerHTML"}
   (ext/csrf-token-html)
   [:div
    [:label {:class ["block" "text-sm" "font-medium" "text-gray-700" "mb-1"]} "公司名稱 *"]
    [:input {:class ["w-full" "border" "border-gray-300" "rounded-lg" "px-3" "py-2" "text-sm"]
             :type "text" :name "name" :value (:name values "") :required true}]
    (for [e (:name errors)]
      [:p {:class ["text-red-500" "text-xs" "mt-1"]} e])]
   [:div
    [:label {:class ["block" "text-sm" "font-medium" "text-gray-700" "mb-1"]} "產業"]
    [:input {:class ["w-full" "border" "border-gray-300" "rounded-lg" "px-3" "py-2" "text-sm"]
             :type "text" :name "industry" :value (:industry values "")}]]
   [:div
    [:label {:class ["block" "text-sm" "font-medium" "text-gray-700" "mb-1"]} "客戶等級"]
    [:select {:class ["w-full" "border" "border-gray-300" "rounded-lg" "px-3" "py-2" "text-sm"]
              :name "tier"}
     (for [[tier-val label] tier-labels]
       [:option {:value tier-val :selected (= tier-val (or (:tier values) "no_plan"))} label])]]
   [:div
    [:label {:class ["block" "text-sm" "font-medium" "text-gray-700" "mb-1"]} "備註"]
    [:textarea {:class ["w-full" "border" "border-gray-300" "rounded-lg" "px-3" "py-2" "text-sm"]
                :name "notes" :rows 3} (:notes values "")]]
   [:div {:class ["flex" "gap-3" "pt-2"]}
    [:button {:class ["bg-indigo-600" "text-white" "px-4" "py-2" "rounded-lg" "text-sm"
                      "hover:bg-indigo-700" "font-medium"] :type "submit"} "儲存"]
    [:a {:class ["border" "border-gray-300" "text-gray-600" "px-4" "py-2" "rounded-lg"
                 "text-sm" "hover:bg-gray-50"]
         :href (ext/get-route router ::routes/companies)} "取消"]]])

(defn new-company-page
  "Full page: new company form."
  [data]
  (base/layout data
    [:div
     [:h1 {:class ["text-2xl" "font-bold" "text-gray-800" "mb-6"]} "新增公司"]
     (new-company-form data)]))

(defn tab-nav
  "Tab switching buttons. Alpine.js tracks activeTab; HTMX fetches content."
  [{:keys [router company]}]
  (let [id (:id company)]
    [:div {:x-data "{ activeTab: 'info' }"
           :class ["border-b" "border-gray-200" "mb-6"]}
     [:div {:class ["flex" "gap-1"]}
      (for [[tab label] [["info" "資訊"] ["contacts" "聯絡人"] ["logs" "聯絡記錄"]]]
        [:button {:class ["px-4" "py-2" "text-sm" "font-medium" "border-b-2" "-mb-px"
                          "transition-colors"]
                  :x-bind:class (str "activeTab === '" tab
                                     "' ? 'border-indigo-500 text-indigo-600'"
                                     " : 'border-transparent text-gray-500 hover:text-gray-700'")
                  :x-on:click (str "activeTab = '" tab "'")
                  :hx-get (str (ext/get-route router ::routes/companies) "/" id "/tabs/" tab)
                  :hx-target "#tab-content"
                  :hx-swap "innerHTML"}
         label])]]))

(defn info-display
  "Read-only view of company info with Edit button."
  [{:keys [router company]}]
  (let [id (:id company)]
    [:div {:id "info-section"}
     [:div {:class ["grid" "grid-cols-2" "gap-4" "mb-4"]}
      [:div
       [:p {:class ["text-xs" "text-gray-400" "uppercase" "tracking-wide"]} "公司名稱"]
       [:p {:class ["font-medium" "text-gray-800"]} (:name company)]]
      [:div
       [:p {:class ["text-xs" "text-gray-400" "uppercase" "tracking-wide"]} "產業"]
       [:p {:class ["text-gray-700"]} (or (:industry company) "—")]]
      [:div
       [:p {:class ["text-xs" "text-gray-400" "uppercase" "tracking-wide"]} "客戶等級"]
       (tier-badge (:tier company))]
      [:div
       [:p {:class ["text-xs" "text-gray-400" "uppercase" "tracking-wide"]} "備註"]
       [:p {:class ["text-gray-700" "text-sm"]} (or (:notes company) "—")]]]
     [:button {:class ["text-sm" "text-indigo-600" "hover:underline"]
               :hx-get (str (ext/get-route router ::routes/companies) "/" id "/tabs/info?editing=true")
               :hx-target "#tab-content"
               :hx-swap "innerHTML"}
      "編輯"]]))

(defn info-edit-form
  "Editable form for company info."
  [{:keys [router company errors]}]
  [:form {:id "info-section"
          :hx-patch (str (ext/get-route router ::routes/companies) "/" (:id company))
          :hx-target "#tab-content"
          :hx-swap "innerHTML"
          :class ["space-y-4"]}
   (ext/csrf-token-html)
   [:div {:class ["grid" "grid-cols-2" "gap-4"]}
    [:div
     [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "公司名稱 *"]
     [:input {:class ["w-full" "border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
              :type "text" :name "name" :value (:name company) :required true}]
     (for [e (:name errors)] [:p {:class ["text-red-500" "text-xs"]} e])]
    [:div
     [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "產業"]
     [:input {:class ["w-full" "border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
              :type "text" :name "industry" :value (or (:industry company) "")}]]
    [:div
     [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "客戶等級"]
     [:select {:class ["w-full" "border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
               :name "tier"}
      (for [[tier-val label] tier-labels]
        [:option {:value tier-val :selected (= tier-val (:tier company))} label])]]
    [:div
     [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "備註"]
     [:textarea {:class ["w-full" "border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
                 :name "notes" :rows 2} (or (:notes company) "")]]]
   [:div {:class ["flex" "gap-2"]}
    [:button {:class ["bg-indigo-600" "text-white" "px-3" "py-1.5" "rounded" "text-sm"
                      "hover:bg-indigo-700"] :type "submit"} "儲存"]
    [:button {:class ["border" "border-gray-300" "text-gray-600" "px-3" "py-1.5" "rounded"
                      "text-sm" "hover:bg-gray-50"]
              :type "button"
              :hx-get (str (ext/get-route router ::routes/companies) "/" (:id company) "/tabs/info")
              :hx-target "#tab-content"
              :hx-swap "innerHTML"}
     "取消"]]])

(defn info-tab-content
  "Info tab: company fields + addresses + phones + (tags placeholder)."
  [{:keys [router company _addresses _phones editing? errors]}]
  [:div
   (if editing?
     (info-edit-form {:router router :company company :errors errors})
     (info-display {:router router :company company}))
   [:div {:id "addresses-section" :class ["mt-6"]}]
   [:div {:id "phones-section" :class ["mt-4"]}]
   [:div {:id "tags-section" :class ["mt-6"]}]])

(defn company-page
  "Full page: company detail with tab shell."
  [{:keys [router company] :as data}]
  (base/layout data
    [:div
     [:div {:class ["flex" "items-center" "gap-3" "mb-6"]}
      [:a {:class ["text-sm" "text-gray-400" "hover:text-gray-600"]
           :href (ext/get-route router ::routes/companies)} "← 公司列表"]
      [:h1 {:class ["text-2xl" "font-bold" "text-gray-800"]} (:name company)]
      (tier-badge (:tier company))]
     (tab-nav {:router router :company company})
     [:div {:id "tab-content"}
      (info-tab-content (assoc data :editing? false))]]))
