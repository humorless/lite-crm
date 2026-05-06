(ns lite-crm.tags.handlers
  "HTTP handlers for interest tags."
  (:require [lite-crm.tags.queries :as queries]
            [reitit-extras.core :as ext]))

(defn tags-section-fragment
  "Shared helper: returns tags-section hiccup for the given entity."
  [db entity-type entity-id]
  (let [tags (queries/list-tags db entity-type entity-id)]
    [:div {:id "tags-section" :class ["mt-6"]}
     [:h3 {:class ["text-sm" "font-semibold" "text-gray-600" "mb-2"]} "興趣標籤"]
     (when (seq tags)
       [:ul {:class ["space-y-1" "mb-3"]}
        (for [t tags]
          [:li {:class ["flex" "items-center" "gap-2" "text-sm"]}
           [:span {:class ["bg-indigo-50" "text-indigo-700" "px-2" "py-0.5"
                           "rounded-full" "text-xs"]} (:name t)]
           (when (:reminder-date t)
             [:span {:class ["text-xs" "text-gray-400"]}
              (str "提醒：" (:reminder-date t))])
           (when (:notes t)
             [:span {:class ["text-xs" "text-gray-400"]} (:notes t)])
           [:button {:class ["ml-auto" "text-red-400" "hover:text-red-600" "text-xs"]
                     :hx-delete (str "/tags/" (:id t))
                     :hx-vals   (str "{\"entity-type\":\"" entity-type
                                     "\",\"entity-id\":\"" entity-id "\"}")
                     :hx-headers (ext/csrf-token-json)
                     :hx-target "#tags-section"
                     :hx-swap   "outerHTML"
                     :hx-confirm "確定移除此標籤？"} "移除"]])])
     [:form {:class ["flex" "gap-2" "items-end" "flex-wrap"]
             :hx-post "/tags"
             :hx-target "#tags-section"
             :hx-swap "outerHTML"}
      (ext/csrf-token-html)
      [:input {:type "hidden" :name "entity-type" :value entity-type}]
      [:input {:type "hidden" :name "entity-id"   :value entity-id}]
      [:input {:class ["border" "border-gray-300" "rounded" "px-2" "py-1" "text-xs" "w-24"]
               :type "text" :name "name" :placeholder "標籤名稱" :required true}]
      [:input {:class ["border" "border-gray-300" "rounded" "px-2" "py-1" "text-xs"]
               :type "date" :name "reminder-date"}]
      [:input {:class ["border" "border-gray-300" "rounded" "px-2" "py-1" "text-xs" "flex-1"]
               :type "text" :name "notes" :placeholder "備註"}]
      [:button {:class ["bg-gray-100" "text-gray-700" "px-2" "py-1" "rounded" "text-xs"
                        "hover:bg-gray-200"] :type "submit"} "新增"]]]))

(defn create-handler
  [{:keys [context parameters]}]
  (let [{:keys [entity-type entity-id reminder-date notes] tag-name :name} (:form parameters)
        entity-id-long (parse-long (str entity-id))]
    (queries/create-tag! (:db context)
                         {:entity-type  entity-type
                          :entity-id    entity-id-long
                          :name         tag-name
                          :reminder-date (when (seq reminder-date) reminder-date)
                          :notes        (when (seq notes) notes)})
    (-> (tags-section-fragment (:db context) entity-type entity-id-long)
        (ext/render-html))))

(defn delete-handler
  [{:keys [context parameters]}]
  (let [id          (get-in parameters [:path :id])
        entity-type (get-in parameters [:form :entity-type])
        entity-id   (parse-long (str (get-in parameters [:form :entity-id])))]
    (queries/delete-tag! (:db context) id)
    (-> (tags-section-fragment (:db context) entity-type entity-id)
        (ext/render-html))))
