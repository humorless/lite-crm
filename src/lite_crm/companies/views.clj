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
