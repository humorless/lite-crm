(ns lite-crm.logs.views
  "Hiccup views for contact logs."
  (:require [lite-crm.logs.queries :as queries]
            [reitit-extras.core :as ext]))

(defn- status-badge [status]
  (let [colors {"no_answer"        "bg-gray-100 text-gray-600"
                "answered_no_talk" "bg-yellow-100 text-yellow-700"
                "sent_intro"       "bg-blue-100 text-blue-700"
                "appointment_set"  "bg-purple-100 text-purple-700"
                "visited"          "bg-green-100 text-green-700"
                "closed"           "bg-emerald-100 text-emerald-700"
                "other"            "bg-gray-100 text-gray-500"}]
    [:span {:class ["inline-flex" "items-center" "px-2" "py-0.5" "rounded" "text-xs"
                    "font-medium" (get colors status "bg-gray-100 text-gray-500")]}
     (queries/status-label status)]))

(defn log-row
  "Single log row inside the logs list."
  [{:keys [log _router]}]
  [:div {:class ["border-t" "border-gray-100" "py-3" "flex" "gap-3"]}
   [:div {:class ["flex-1"]}
    [:div {:class ["flex" "items-center" "gap-2" "mb-1"]}
     [:span {:class ["text-xs" "text-gray-400"]} (:date log)]
     (when (:status log) (status-badge (:status log)))
     (when (pos? (:is-pinned log))
       [:span {:class ["text-xs" "text-orange-500"]} "置頂"])
     (when (:contact-names log)
       [:span {:class ["text-xs" "text-gray-500"]} (:contact-names log)])]
    [:p {:class ["text-sm" "text-gray-800"]} (:content log)]]
   [:div {:class ["flex" "gap-2" "items-start" "shrink-0"]}
    [:button {:class ["text-xs" "text-gray-400" "hover:text-orange-500"]
              :hx-patch (str "/logs/" (:id log))
              :hx-vals (str "{\"is-pinned\":\"" (if (pos? (:is-pinned log)) "false" "true") "\"}")
              :hx-headers (ext/csrf-token-json)
              :hx-target "#logs-list"
              :hx-swap "innerHTML"}
     (if (pos? (:is-pinned log)) "取消置頂" "置頂")]
    [:button {:class ["text-xs" "text-gray-400" "hover:text-red-500"]
              :hx-delete (str "/logs/" (:id log))
              :hx-headers (ext/csrf-token-json)
              :hx-target "#logs-list"
              :hx-swap "innerHTML"
              :hx-confirm "確定刪除這筆記錄嗎？"}
     "刪除"]]])

(defn logs-list-fragment
  "The #logs-list div — used as HTMX swap target."
  [{:keys [logs router]}]
  (if (seq logs)
    [:div {:id "logs-list"}
     (for [log logs] (log-row {:log log :router router}))]
    [:div {:id "logs-list" :class ["py-6" "text-center" "text-gray-400" "text-sm"]}
     "尚無聯絡記錄"]))

(defn add-log-form
  "Inline form for creating a new log."
  [{:keys [company-id contacts]}]
  [:form {:class ["bg-gray-50" "rounded-lg" "p-4" "mb-4" "space-y-3"]
          :hx-post (str "/companies/" company-id "/logs")
          :hx-target "#logs-list"
          :hx-swap "innerHTML"
          :x-show "showForm"}
   (ext/csrf-token-html)
   [:div {:class ["flex" "gap-3"]}
    [:div
     [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "日期"]
     [:input {:class ["border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
              :type "date" :name "date" :required true}]]
    [:div
     [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "狀態"]
     [:select {:class ["border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"] :name "status"}
      [:option {:value ""} "（無）"]
      (for [[status-val label] {"no_answer"        "未接"
                               "answered_no_talk" "接通沒談"
                               "sent_intro"       "寄送自介信"
                               "appointment_set"  "已約訪"
                               "visited"          "已拜訪"
                               "closed"           "成交"
                               "other"            "其他"}]
        [:option {:value status-val} label])]]
    (when (seq contacts)
      [:div
       [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "聯絡人"]
       [:select {:class ["border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
                 :name "contact-ids" :multiple true}
        (for [c contacts]
          [:option {:value (:id c)} (:name c)])]])]
   [:div
    [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "內容 *"]
    [:textarea {:class ["w-full" "border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
                :name "content" :rows 3 :required true}]]
   [:div {:class ["flex" "gap-2"]}
    [:button {:class ["bg-indigo-600" "text-white" "px-3" "py-1.5" "rounded" "text-sm"
                      "hover:bg-indigo-700"] :type "submit"} "儲存"]
    [:button {:class ["border" "border-gray-300" "text-gray-600" "px-3" "py-1.5" "rounded"
                      "text-sm" "hover:bg-gray-50"]
              :type "button" :x-on:click "showForm = false"} "取消"]]])

(defn logs-tab-content
  "Full logs tab: add-log toggle + logs list."
  [{:keys [company logs contacts]}]
  [:div {:x-data "{ showForm: false }"}
   [:div {:class ["flex" "justify-end" "mb-3"]}
    [:button {:class ["bg-indigo-600" "text-white" "px-3" "py-1.5" "rounded" "text-sm"
                      "hover:bg-indigo-700"] :x-on:click "showForm = true"}
     "+ 新增記錄"]]
   (add-log-form {:company-id (:id company) :contacts contacts})
   (logs-list-fragment {:logs logs})])
