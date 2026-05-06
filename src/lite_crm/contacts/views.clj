(ns lite-crm.contacts.views
  "Hiccup views for contacts list, create, and detail."
  (:require [lite-crm.routes :as-alias routes]
            [lite-crm.views :as base]
            [reitit-extras.core :as ext]))

(defn contact-row
  [{:keys [router contact]}]
  (let [detail-url (str (ext/get-route router ::routes/contact) "/" (:id contact))]
    [:tr {:class ["hover:bg-gray-50"]}
     [:td {:class ["px-4" "py-3" "font-medium" "text-indigo-600"]}
      [:a {:href detail-url} (:name contact)]]
     [:td {:class ["px-4" "py-3" "text-sm" "text-gray-600"]}
      (when (:company-id contact)
        [:a {:class ["hover:underline"]
             :href  (str (ext/get-route router ::routes/companies) "/" (:company-id contact))}
         (:company-name contact)])]
     [:td {:class ["px-4" "py-3" "text-sm" "text-gray-600"]} (or (:title contact) "—")]
     [:td {:class ["px-4" "py-3" "text-sm" "text-gray-600"]} (or (:mobile contact) "—")]
     [:td {:class ["px-4" "py-3" "text-sm" "text-gray-600"]} (or (:email contact) "—")]
     [:td {:class ["px-4" "py-3"]}
      [:a {:class ["text-xs" "text-gray-400" "hover:text-indigo-600"]
           :href  (str detail-url "/vcard")} "vCard"]]]))

(defn contacts-page
  [{:keys [router contacts] :as data}]
  (base/layout data
    [:div
     [:div {:class ["flex" "items-center" "justify-between" "mb-6"]}
      [:h1 {:class ["text-2xl" "font-bold" "text-gray-800"]} "聯絡人"]
      [:a {:class ["bg-indigo-600" "text-white" "px-4" "py-2" "rounded" "text-sm"
                   "hover:bg-indigo-700"]
           :href (str (ext/get-route router ::routes/contacts) "/new")}
       "+ 新增聯絡人"]]
     [:div {:class ["bg-white" "rounded-lg" "shadow" "overflow-hidden"]}
      [:table {:class ["min-w-full"]}
       [:thead {:class ["bg-gray-50" "border-b" "border-gray-200"]}
        [:tr
         (for [h ["姓名" "公司" "職稱" "行動電話" "Email" ""]]
           [:th {:class ["px-4" "py-3" "text-left" "text-xs" "font-medium"
                         "text-gray-500" "uppercase" "tracking-wide"]} h])]]
       [:tbody {:class ["divide-y" "divide-gray-100"]}
        (if (seq contacts)
          (for [c contacts] (contact-row {:router router :contact c}))
          [:tr [:td {:class ["px-4" "py-8" "text-center" "text-gray-400"] :colspan 6}
                "尚無聯絡人"]])]]]]))

(defn new-contact-page
  [{:keys [router companies errors params] :as data}]
  (base/layout data
    [:div {:class ["max-w-lg" "mx-auto"]}
     [:h1 {:class ["text-2xl" "font-bold" "text-gray-800" "mb-6"]} "新增聯絡人"]
     [:form {:method "post"
             :action (ext/get-route router ::routes/contacts)
             :class  ["bg-white" "rounded-lg" "shadow" "p-6" "space-y-4"]}
      (ext/csrf-token-html)
      [:div
       [:label {:class ["block" "text-sm" "font-medium" "text-gray-700" "mb-1"]} "姓名 *"]
       [:input {:class ["w-full" "border" "border-gray-300" "rounded" "px-3" "py-2" "text-sm"]
                :type "text" :name "name" :value (or (:name params) "") :required true}]
       (for [e (:name errors)] [:p {:class ["text-red-500" "text-xs" "mt-1"]} e])]
      [:div
       [:label {:class ["block" "text-sm" "font-medium" "text-gray-700" "mb-1"]} "公司"]
       [:select {:class ["w-full" "border" "border-gray-300" "rounded" "px-3" "py-2" "text-sm"]
                 :name "company-id"}
        [:option {:value ""} "（未關聯）"]
        (for [co companies]
          [:option {:value (:id co) :selected (= (str (:id co)) (str (:company-id params)))}
           (:name co)])]]
      [:div {:class ["grid" "grid-cols-2" "gap-4"]}
       [:div
        [:label {:class ["block" "text-sm" "font-medium" "text-gray-700" "mb-1"]} "部門"]
        [:input {:class ["w-full" "border" "border-gray-300" "rounded" "px-3" "py-2" "text-sm"]
                 :type "text" :name "department" :value (or (:department params) "")}]]
       [:div
        [:label {:class ["block" "text-sm" "font-medium" "text-gray-700" "mb-1"]} "職稱"]
        [:input {:class ["w-full" "border" "border-gray-300" "rounded" "px-3" "py-2" "text-sm"]
                 :type "text" :name "title" :value (or (:title params) "")}]]]
      [:div {:class ["grid" "grid-cols-2" "gap-4"]}
       [:div
        [:label {:class ["block" "text-sm" "font-medium" "text-gray-700" "mb-1"]} "電話"]
        [:input {:class ["w-full" "border" "border-gray-300" "rounded" "px-3" "py-2" "text-sm"]
                 :type "text" :name "phone" :value (or (:phone params) "")}]]
       [:div
        [:label {:class ["block" "text-sm" "font-medium" "text-gray-700" "mb-1"]} "分機"]
        [:input {:class ["w-full" "border" "border-gray-300" "rounded" "px-3" "py-2" "text-sm"]
                 :type "text" :name "phone-ext" :value (or (:phone-ext params) "")}]]]
      [:div {:class ["grid" "grid-cols-2" "gap-4"]}
       [:div
        [:label {:class ["block" "text-sm" "font-medium" "text-gray-700" "mb-1"]} "行動電話"]
        [:input {:class ["w-full" "border" "border-gray-300" "rounded" "px-3" "py-2" "text-sm"]
                 :type "text" :name "mobile" :value (or (:mobile params) "")}]]
       [:div
        [:label {:class ["block" "text-sm" "font-medium" "text-gray-700" "mb-1"]} "Email"]
        [:input {:class ["w-full" "border" "border-gray-300" "rounded" "px-3" "py-2" "text-sm"]
                 :type "email" :name "email" :value (or (:email params) "")}]]]
      [:div
       [:label {:class ["block" "text-sm" "font-medium" "text-gray-700" "mb-1"]} "備註"]
       [:textarea {:class ["w-full" "border" "border-gray-300" "rounded" "px-3" "py-2" "text-sm"]
                   :name "notes" :rows 3} (or (:notes params) "")]]
      [:div {:class ["flex" "gap-3"]}
       [:button {:class ["bg-indigo-600" "text-white" "px-4" "py-2" "rounded" "text-sm"
                         "hover:bg-indigo-700"] :type "submit"} "新增"]
       [:a {:class ["border" "border-gray-300" "text-gray-600" "px-4" "py-2" "rounded"
                    "text-sm" "hover:bg-gray-50"]
            :href (ext/get-route router ::routes/contacts)} "取消"]]]]))

(defn contact-info-display
  [{:keys [router contact]}]
  (let [id (:id contact)]
    [:div {:id "contact-info"}
     [:div {:class ["grid" "grid-cols-2" "gap-4" "mb-4"]}
      (for [[label field-val] [["公司"       (when (:company-id contact)
                                         [:a {:class ["text-indigo-600" "hover:underline"]
                                              :href (str (ext/get-route router ::routes/companies)
                                                         "/" (:company-id contact))}
                                          (:company-name contact)])]
                         ["部門"       (:department contact)]
                         ["職稱"       (:title contact)]
                         ["電話"       (str (or (:phone contact) "")
                                            (when (:phone-ext contact)
                                              (str " 分機 " (:phone-ext contact))))]
                         ["行動電話"   (:mobile contact)]
                         ["Email"      (:email contact)]
                         ["備註"       (:notes contact)]]]
        [:div
         [:p {:class ["text-xs" "text-gray-400" "uppercase" "tracking-wide"]} label]
         [:p {:class ["text-sm" "text-gray-700"]} (or field-val "—")]])]
     [:button {:class ["text-sm" "text-indigo-600" "hover:underline"]
               :hx-get (str "/contacts/" id "?editing=true")
               :hx-target "#contact-info"
               :hx-swap "outerHTML"} "編輯"]]))

(defn contact-info-edit-form
  [{:keys [contact companies errors]}]
  [:form {:id "contact-info"
          :hx-patch (str "/contacts/" (:id contact))
          :hx-target "#contact-info"
          :hx-swap "outerHTML"
          :class ["space-y-4"]}
   (ext/csrf-token-html)
   [:div {:class ["grid" "grid-cols-2" "gap-4"]}
    [:div
     [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "姓名 *"]
     [:input {:class ["w-full" "border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
              :type "text" :name "name" :value (:name contact) :required true}]
     (for [e (:name errors)] [:p {:class ["text-red-500" "text-xs"]} e])]
    [:div
     [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "公司"]
     [:select {:class ["w-full" "border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
               :name "company-id"}
      [:option {:value ""} "（未關聯）"]
      (for [co companies]
        [:option {:value (:id co) :selected (= (:id co) (:company-id contact))}
         (:name co)])]]
    [:div
     [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "部門"]
     [:input {:class ["w-full" "border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
              :type "text" :name "department" :value (or (:department contact) "")}]]
    [:div
     [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "職稱"]
     [:input {:class ["w-full" "border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
              :type "text" :name "title" :value (or (:title contact) "")}]]
    [:div
     [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "電話"]
     [:input {:class ["w-full" "border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
              :type "text" :name "phone" :value (or (:phone contact) "")}]]
    [:div
     [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "分機"]
     [:input {:class ["w-full" "border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
              :type "text" :name "phone-ext" :value (or (:phone-ext contact) "")}]]
    [:div
     [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "行動電話"]
     [:input {:class ["w-full" "border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
              :type "text" :name "mobile" :value (or (:mobile contact) "")}]]
    [:div
     [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "Email"]
     [:input {:class ["w-full" "border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
              :type "email" :name "email" :value (or (:email contact) "")}]]
    [:div {:class ["col-span-2"]}
     [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "備註"]
     [:textarea {:class ["w-full" "border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
                 :name "notes" :rows 2} (or (:notes contact) "")]]]
   [:div {:class ["flex" "gap-2"]}
    [:button {:class ["bg-indigo-600" "text-white" "px-3" "py-1.5" "rounded" "text-sm"
                      "hover:bg-indigo-700"] :type "submit"} "儲存"]
    [:button {:class ["border" "border-gray-300" "text-gray-600" "px-3" "py-1.5" "rounded"
                      "text-sm" "hover:bg-gray-50"]
              :type "button"
              :hx-get (str "/contacts/" (:id contact))
              :hx-target "#contact-info"
              :hx-swap "outerHTML"} "取消"]]])

(defn contact-page
  [{:keys [router contact companies contact-logs editing? errors tags-section] :as data}]
  (base/layout data
    [:div
     [:div {:class ["flex" "items-center" "gap-3" "mb-6"]}
      [:a {:class ["text-sm" "text-gray-400" "hover:text-gray-600"]
           :href (ext/get-route router ::routes/contacts)} "← 聯絡人列表"]
      [:h1 {:class ["text-2xl" "font-bold" "text-gray-800"]} (:name contact)]
      [:a {:class ["ml-auto" "text-sm" "text-gray-400" "hover:text-indigo-600" "border"
                   "border-gray-200" "rounded" "px-3" "py-1"]
           :href (str "/contacts/" (:id contact) "/vcard")} "下載 vCard"]]
     [:div {:class ["grid" "grid-cols-3" "gap-6"]}
      [:div {:class ["col-span-2" "bg-white" "rounded-lg" "shadow" "p-6"]}
       (if editing?
         (contact-info-edit-form {:router router :contact contact
                                  :companies companies :errors errors})
         (contact-info-display {:router router :contact contact}))
       (or tags-section [:div {:id "tags-section" :class ["mt-6"]}])]
      [:div {:class ["bg-white" "rounded-lg" "shadow" "p-6"]}
       [:h2 {:class ["text-sm" "font-semibold" "text-gray-600" "mb-3"]} "聯絡記錄"]
       (if (seq contact-logs)
         [:ul {:class ["space-y-3"]}
          (for [log contact-logs]
            [:li {:class ["border-t" "border-gray-100" "pt-3" "text-sm"]}
             [:div {:class ["flex" "items-center" "gap-2" "mb-1"]}
              [:span {:class ["text-xs" "text-gray-400"]} (:date log)]
              [:a {:class ["text-xs" "text-indigo-500" "hover:underline"]
                   :href (str (ext/get-route router ::routes/companies)
                              "/" (:company-id log))}
               (:company-name log)]]
             [:p {:class ["text-gray-700"]}
              (let [c (:content log)]
                (if (> (count c) 80) (str (subs c 0 80) "…") c))]])]
         [:p {:class ["text-xs" "text-gray-400"]} "尚無記錄"])]]]))

(defn company-contacts-tab
  "Contacts tab fragment inside company detail."
  [{:keys [router company contacts]}]
  (let [new-url (str (ext/get-route router ::routes/contacts)
                     "/new?company-id=" (:id company))]
    [:div
     [:div {:class ["flex" "justify-end" "mb-3"]}
      [:a {:class ["bg-indigo-600" "text-white" "px-3" "py-1.5" "rounded" "text-sm"
                   "hover:bg-indigo-700"]
           :href new-url} "+ 新增聯絡人"]]
     (if (seq contacts)
       [:table {:class ["min-w-full"]}
        [:thead {:class ["bg-gray-50"]}
         [:tr
          (for [h ["姓名" "職稱" "部門" "行動電話" "Email"]]
            [:th {:class ["px-3" "py-2" "text-left" "text-xs" "font-medium"
                          "text-gray-500" "uppercase"]} h])]]
        [:tbody {:class ["divide-y" "divide-gray-100"]}
         (for [c contacts]
           [:tr {:class ["hover:bg-gray-50"]}
            [:td {:class ["px-3" "py-2" "text-sm" "font-medium" "text-indigo-600"]}
             [:a {:href (str "/contacts/" (:id c))} (:name c)]]
            [:td {:class ["px-3" "py-2" "text-sm" "text-gray-600"]} (or (:title c) "—")]
            [:td {:class ["px-3" "py-2" "text-sm" "text-gray-600"]} (or (:department c) "—")]
            [:td {:class ["px-3" "py-2" "text-sm" "text-gray-600"]} (or (:mobile c) "—")]
            [:td {:class ["px-3" "py-2" "text-sm" "text-gray-600"]} (or (:email c) "—")]])]]
       [:p {:class ["text-center" "text-gray-400" "py-6" "text-sm"]} "尚無聯絡人"])]))
