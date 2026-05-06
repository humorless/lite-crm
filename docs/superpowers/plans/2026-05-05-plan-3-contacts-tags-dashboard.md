# Lite CRM — Plan 3: Contacts, Tags & Dashboard

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Contacts module (list, create, detail, vCard), Interest Tags CRUD on both companies and contacts, the Dashboard (reminders + recent logs), and the Global Logs Ledger with filters.

**Architecture:** Contacts follow the same pattern as Companies. Tags are polymorphic (`entity_type` + `entity_id`) served from a top-level `/tags` route group. Dashboard is a direct query on page load — no background jobs. Logs Ledger reuses `logs/views.clj` with an added filter bar.

**Tech Stack:** Same as Plans 1 & 2. Requires both Plan 1 and Plan 2 to be complete.

---

## File Structure

**Create:**
- `src/lite_crm/contacts/queries.clj`
- `src/lite_crm/contacts/handlers.clj`
- `src/lite_crm/contacts/views.clj`
- `src/lite_crm/tags/queries.clj`
- `src/lite_crm/tags/handlers.clj`
- `test/lite_crm/contacts_test.clj`
- `test/lite_crm/tags_test.clj`

**Modify:**
- `src/lite_crm/handlers.clj` — update home-handler for dashboard
- `src/lite_crm/views.clj` — add home-page dashboard panels
- `src/lite_crm/companies/handlers.clj` — wire contacts tab
- `src/lite_crm/logs/queries.clj` — add list-all-logs query
- `src/lite_crm/logs/handlers.clj` — add ledger handler
- `src/lite_crm/logs/views.clj` — add ledger page view
- `src/lite_crm/routes.clj` — add contacts + tags + ledger routes

---

## Task 8: Contacts Module

**Files:**
- Create: `src/lite_crm/contacts/queries.clj`
- Create: `src/lite_crm/contacts/handlers.clj`
- Create: `src/lite_crm/contacts/views.clj`
- Create: `test/lite_crm/contacts_test.clj`
- Modify: `src/lite_crm/companies/handlers.clj` — wire contacts tab
- Modify: `src/lite_crm/routes.clj`

- [ ] **Step 1: Write failing tests**

```clojure
;; test/lite_crm/contacts_test.clj
(ns lite-crm.contacts-test
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [hickory.select :as select]
            [integrant-extras.tests :as ig-extras]
            [lite-crm.auth.queries :as auth-queries]
            [lite-crm.companies.queries :as company-queries]
            [lite-crm.contacts.queries :as contact-queries]
            [lite-crm.test-utils :as utils]
            [reitit-extras.tests :as reitit-extras]))

(use-fixtures :once (ig-extras/with-system))
(use-fixtures :each utils/with-truncated-tables)

(deftest test-contacts-list-requires-auth
  (let [url      (str (reitit-extras/get-server-url (utils/server)) "/contacts")
        response (http/get url {:redirect-strategy :none})]
    (is (= 302 (:status response)))))

(deftest test-create-contact
  (let [user     (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        company  (company-queries/create-company! (utils/db) {:name "台積電" :tier "no_plan"})
        url      (str (reitit-extras/get-server-url (utils/server)) "/contacts")
        response (http/post url {:cookies     (utils/auth-cookies-with-csrf user)
                                 :form-params {reitit-extras/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :name       "王小明"
                                               :company-id (:id company)
                                               :title      "業務經理"}})]
    (is (= 302 (:status response)))
    (is (= 1 (count (contact-queries/list-contacts (utils/db) {}))))))

(deftest test-contact-detail-shows-name
  (let [user    (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        contact (contact-queries/create-contact! (utils/db) {:name "李小華" :email "li@test.com"})
        url     (str (reitit-extras/get-server-url (utils/server)) "/contacts/" (:id contact))
        body    (utils/response->hickory (http/get url {:cookies (utils/auth-cookies user)}))]
    (is (some #(= ["李小華"] (:content %))
              (select/select (select/tag :h1) body)))))

(deftest test-update-contact
  (let [user    (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        contact (contact-queries/create-contact! (utils/db) {:name "舊名字"})
        url     (str (reitit-extras/get-server-url (utils/server)) "/contacts/" (:id contact))
        response (http/patch url {:cookies     (utils/auth-cookies-with-csrf user)
                                  :form-params {reitit-extras/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                                :name "新名字" :title "總監"}})]
    (is (= 200 (:status response)))
    (is (= "新名字" (:name (contact-queries/get-contact (utils/db) (:id contact)))))))
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
bb test
```

Expected: `lite-crm.contacts.queries` not found.

- [ ] **Step 3: Write `contacts/queries.clj`**

```clojure
;; src/lite_crm/contacts/queries.clj
(ns lite-crm.contacts.queries
  (:require [lite-crm.db :as db]))

(defn list-contacts
  "Return contacts filtered by optional :company-id, :name, :email."
  [db {:keys [company-id name email]}]
  (let [wheres (cond-> []
                 company-id (conj [:= :c/company-id company-id])
                 name       (conj [:like :c/name (str "%" name "%")])
                 email      (conj [:like :c/email (str "%" email "%")]))
        query  {:select   [:c/id :c/name :c/department :c/title :c/phone :c/phone-ext
                            :c/mobile :c/email :c/notes :c/created-at
                            [:co/name :company-name] [:co/id :company-id]]
                :from     [[:contact :c]]
                :left-join [[:company :co] [:= :co/id :c/company-id]]
                :order-by [[:c/name :asc]]}
        query  (if (seq wheres) (assoc query :where (into [:and] wheres)) query)]
    (db/exec! db query)))

(defn get-contact
  [db id]
  (db/exec-one! db {:select    [:c/id :c/name :c/company-id :c/department :c/title
                                  :c/phone :c/phone-ext :c/mobile :c/email :c/notes
                                  :c/created-at [:co/name :company-name]]
                    :from      [[:contact :c]]
                    :left-join [[:company :co] [:= :co/id :c/company-id]]
                    :where     [:= :c/id id]}))

(defn create-contact!
  [db {:keys [company-id name department title phone phone-ext mobile email notes]}]
  (db/exec-one! db {:insert-into :contact
                    :values      [(cond-> {:name name}
                                    company-id  (assoc :company-id company-id)
                                    department  (assoc :department department)
                                    title       (assoc :title title)
                                    phone       (assoc :phone phone)
                                    phone-ext   (assoc :phone-ext phone-ext)
                                    mobile      (assoc :mobile mobile)
                                    email       (assoc :email email)
                                    notes       (assoc :notes notes))]
                    :returning   [:*]}))

(defn update-contact!
  [db id {:keys [company-id name department title phone phone-ext mobile email notes]}]
  (db/exec-one! db {:update    :contact
                    :set       (cond-> {}
                                 (some? name)       (assoc :name name)
                                 (some? company-id) (assoc :company-id company-id)
                                 (some? department) (assoc :department department)
                                 (some? title)      (assoc :title title)
                                 (some? phone)      (assoc :phone phone)
                                 (some? phone-ext)  (assoc :phone-ext phone-ext)
                                 (some? mobile)     (assoc :mobile mobile)
                                 (some? email)      (assoc :email email)
                                 (some? notes)      (assoc :notes notes))
                    :where     [:= :id id]
                    :returning [:*]}))

(defn list-contacts-by-company
  [db company-id]
  (db/exec! db {:select [:id :name :department :title :phone :phone-ext :mobile :email]
                :from [:contact]
                :where [:= :company-id company-id]
                :order-by [[:name :asc]]}))

(defn list-logs-for-contact
  "All logs this contact appears in, newest first."
  [db contact-id]
  (db/exec! db {:select   [:cl/id :cl/date :cl/content :cl/status :cl/is-pinned
                            [:co/id :company-id] [:co/name :company-name]]
                :from     [[:log-contact :lc]]
                :join     [[:contact-log :cl] [:= :cl/id :lc/log-id]
                           [:company :co]     [:= :co/id :cl/company-id]]
                :where    [:= :lc/contact-id contact-id]
                :order-by [[:cl/date :desc]]}))
```

- [ ] **Step 4: Write `contacts/views.clj`**

```clojure
;; src/lite_crm/contacts/views.clj
(ns lite-crm.contacts.views
  (:require [lite-crm.views.base :as base]
            [lite-crm.routes :as-alias routes]
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
  [{:keys [user router contacts] :as data}]
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
  [{:keys [user router companies errors params] :as data}]
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
      (for [[label val] [["公司"       (when (:company-id contact)
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
         [:p {:class ["text-sm" "text-gray-700"]} (or val "—")]])]
     [:button {:class ["text-sm" "text-indigo-600" "hover:underline"]
               :hx-get (str "/contacts/" id "?editing=true")
               :hx-target "#contact-info"
               :hx-swap "outerHTML"} "編輯"]]))

(defn contact-info-edit-form
  [{:keys [router contact companies errors]}]
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
  [{:keys [user router contact companies contact-logs editing? errors] :as data}]
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
       ; tags-section added in Task 9
       [:div {:id "tags-section" :class ["mt-6"]}]]
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
         [:p {:class ["text-xs" "text-gray-400"]} "尚無記錄"])]]))
```

- [ ] **Step 5: Write `contacts/handlers.clj`**

```clojure
;; src/lite_crm/contacts/handlers.clj
(ns lite-crm.contacts.handlers
  (:require [lite-crm.companies.queries :as company-queries]
            [lite-crm.contacts.queries :as queries]
            [lite-crm.contacts.views :as views]
            [reitit-extras.core :as ext]
            [ring.util.response :as response]))

(defn list-handler
  [{:keys [context query-params]
    user   :identity
    router :reitit.core/router}]
  (let [contacts (queries/list-contacts (:db context)
                                        {:name  (:name query-params)
                                         :email (:email query-params)})]
    (-> {:user user :router router :contacts contacts}
        (views/contacts-page)
        (ext/render-html))))

(defn new-handler
  [{:keys [context query-params]
    user   :identity
    router :reitit.core/router}]
  (let [companies (company-queries/list-companies (:db context) {})]
    (-> {:user user :router router :companies companies
         :params {:company-id (:company-id query-params)}}
        (views/new-contact-page)
        (ext/render-html))))

(defn create-handler
  [{:keys [context parameters errors]
    user   :identity
    router :reitit.core/router}]
  (if (some? errors)
    (let [companies (company-queries/list-companies (:db context) {})]
      (-> {:user user :router router :companies companies
           :params (:form parameters) :errors (:humanized errors)}
          (views/new-contact-page)
          (ext/render-html)))
    (let [{:keys [name company-id department title phone phone-ext mobile email notes]}
          (:form parameters)
          company-id-long (when (seq company-id) (parse-long company-id))
          contact (queries/create-contact! (:db context)
                                           {:name       name
                                            :company-id company-id-long
                                            :department department
                                            :title      title
                                            :phone      phone
                                            :phone-ext  phone-ext
                                            :mobile     mobile
                                            :email      email
                                            :notes      notes})]
      (response/redirect (str "/contacts/" (:id contact)) :see-other))))

(defn detail-handler
  [{:keys [context parameters query-params]
    user   :identity
    router :reitit.core/router}]
  (let [id           (get-in parameters [:path :id])
        contact      (queries/get-contact (:db context) id)
        companies    (company-queries/list-companies (:db context) {})
        contact-logs (queries/list-logs-for-contact (:db context) id)
        editing?     (= "true" (:editing query-params))]
    (if (nil? contact)
      (response/not-found "Contact not found")
      (-> {:user user :router router :contact contact
           :companies companies :contact-logs contact-logs :editing? editing?}
          (views/contact-page)
          (ext/render-html)))))

(defn update-handler
  [{:keys [context parameters errors]
    user   :identity
    router :reitit.core/router}]
  (let [id        (get-in parameters [:path :id])
        form      (:form parameters)
        companies (company-queries/list-companies (:db context) {})]
    (if (some? errors)
      (let [contact (queries/get-contact (:db context) id)]
        (-> {:user user :router router :contact (merge contact form)
             :companies companies :editing? true :errors (:humanized errors)}
            (views/contact-info-edit-form)
            (ext/render-html)))
      (let [{:keys [name company-id department title phone phone-ext mobile email notes]} form
            company-id-long (when (seq (str company-id)) (parse-long (str company-id)))]
        (queries/update-contact! (:db context) id
                                 {:name       name
                                  :company-id company-id-long
                                  :department department
                                  :title      title
                                  :phone      phone
                                  :phone-ext  phone-ext
                                  :mobile     mobile
                                  :email      email
                                  :notes      notes})
        (let [contact (queries/get-contact (:db context) id)]
          (-> {:router router :contact contact}
              (views/contact-info-display)
              (ext/render-html)))))))
```

- [ ] **Step 6: Wire contacts tab in `companies/handlers.clj`**

Add to requires:
```clojure
[lite-crm.contacts.queries :as contact-queries]
[lite-crm.contacts.views :as contact-views]
```

Update `tab-handler` `"contacts"` branch:
```clojure
"contacts"
(let [company  (queries/get-company (:db context) id)
      contacts (contact-queries/list-contacts-by-company (:db context) id)]
  (-> {:router router :company company :contacts contacts}
      (contact-views/company-contacts-tab)
      (ext/render-html)))
```

Add `company-contacts-tab` view to `contacts/views.clj`:
```clojure
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
```

- [ ] **Step 7: Add routes to `routes.clj`**

Add requires: `[lite-crm.contacts.handlers :as contact-handlers]`

Add top-level route group (sibling of `/companies` and `/logs`):
```clojure
["/contacts"
 {:middleware [[auth-middleware/wrap-authentication auth-backend] wrap-login-required]}
 [""  {:name ::contacts
       :get  {:handler contact-handlers/list-handler
              :responses {200 {:body string?}}}
       :post {:handler    contact-handlers/create-handler
              :parameters {:form [:map
                                  [:name       [:string {:min 1}]]
                                  [:company-id {:optional true} string?]
                                  [:department {:optional true} string?]
                                  [:title      {:optional true} string?]
                                  [:phone      {:optional true} string?]
                                  [:phone-ext  {:optional true} string?]
                                  [:mobile     {:optional true} string?]
                                  [:email      {:optional true} string?]
                                  [:notes      {:optional true} string?]]}
              :responses  {302 {:body string?}}}}]
 ["/new" {:name ::contacts-new
          :get  {:handler contact-handlers/new-handler
                 :responses {200 {:body string?}}}}]
 ["/:id"
  {:parameters {:path [:map [:id pos-int?]]}}
  ["" {:name  ::contact
       :get   {:handler contact-handlers/detail-handler
               :responses {200 {:body string?}}}
       :patch {:handler    contact-handlers/update-handler
               :parameters {:form [:map
                                   [:name       {:optional true} [:string {:min 1}]]
                                   [:company-id {:optional true} string?]
                                   [:department {:optional true} string?]
                                   [:title      {:optional true} string?]
                                   [:phone      {:optional true} string?]
                                   [:phone-ext  {:optional true} string?]
                                   [:mobile     {:optional true} string?]
                                   [:email      {:optional true} string?]
                                   [:notes      {:optional true} string?]]}
               :responses  {200 {:body string?}}}}]]]
```

- [ ] **Step 8: Run tests — expect PASS**

```bash
bb test
```

Expected: 4 new contacts tests pass.

- [ ] **Step 9: Commit**

```bash
git add src/lite_crm/contacts/ src/lite_crm/companies/handlers.clj \
        src/lite_crm/routes.clj test/lite_crm/contacts_test.clj
git commit -m "Add contacts module with list, create, detail, and company contacts tab"
```

---

## Task 9: Interest Tags

**Files:**
- Create: `src/lite_crm/tags/queries.clj`
- Create: `src/lite_crm/tags/handlers.clj`
- Create: `test/lite_crm/tags_test.clj`
- Modify: `src/lite_crm/companies/views.clj` — add tags-section
- Modify: `src/lite_crm/companies/handlers.clj` — pass tags into info tab
- Modify: `src/lite_crm/contacts/views.clj` — add tags-section
- Modify: `src/lite_crm/contacts/handlers.clj` — pass tags into contact page
- Modify: `src/lite_crm/routes.clj`

- [ ] **Step 1: Write failing tests**

```clojure
;; test/lite_crm/tags_test.clj
(ns lite-crm.tags-test
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [integrant-extras.tests :as ig-extras]
            [lite-crm.auth.queries :as auth-queries]
            [lite-crm.companies.queries :as company-queries]
            [lite-crm.contacts.queries :as contact-queries]
            [lite-crm.tags.queries :as tag-queries]
            [lite-crm.test-utils :as utils]
            [reitit-extras.tests :as reitit-extras]))

(use-fixtures :once (ig-extras/with-system))
(use-fixtures :each utils/with-truncated-tables)

(deftest test-add-tag-to-company
  (let [user    (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        company (company-queries/create-company! (utils/db) {:name "台積電" :tier "no_plan"})
        url     (str (reitit-extras/get-server-url (utils/server)) "/tags")
        response (http/post url {:cookies     (utils/auth-cookies-with-csrf user)
                                 :form-params {reitit-extras/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :entity-type    "company"
                                               :entity-id      (:id company)
                                               :name           "MDS"
                                               :reminder-date  "2026-06-01"}})]
    (is (= 200 (:status response)))
    (is (= 1 (count (tag-queries/list-tags (utils/db) "company" (:id company)))))))

(deftest test-delete-tag
  (let [user    (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        company (company-queries/create-company! (utils/db) {:name "台積電" :tier "no_plan"})
        tag     (tag-queries/create-tag! (utils/db)
                                          {:entity-type "company"
                                           :entity-id   (:id company)
                                           :name        "MDS"})
        url     (str (reitit-extras/get-server-url (utils/server)) "/tags/" (:id tag))
        response (http/delete url {:cookies     (utils/auth-cookies-with-csrf user)
                                   :form-params {reitit-extras/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                                 :entity-type "company"
                                                 :entity-id   (:id company)}})]
    (is (= 200 (:status response)))
    (is (zero? (count (tag-queries/list-tags (utils/db) "company" (:id company)))))))
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
bb test
```

Expected: `lite-crm.tags.queries` not found.

- [ ] **Step 3: Write `tags/queries.clj`**

```clojure
;; src/lite_crm/tags/queries.clj
(ns lite-crm.tags.queries
  (:require [lite-crm.db :as db]))

(defn list-tags
  "Return all tags for an entity."
  [db entity-type entity-id]
  (db/exec! db {:select   [:*]
                :from     [:interest-tag]
                :where    [:and
                           [:= :entity-type entity-type]
                           [:= :entity-id entity-id]]
                :order-by [[:created-at :asc]]}))

(defn create-tag!
  [db {:keys [entity-type entity-id name reminder-date notes]}]
  (db/exec-one! db {:insert-into :interest-tag
                    :values      [(cond-> {:entity-type entity-type
                                           :entity-id   entity-id
                                           :name        name}
                                    reminder-date (assoc :reminder-date reminder-date)
                                    notes         (assoc :notes notes))]
                    :returning   [:*]}))

(defn delete-tag!
  [db id]
  (db/exec-one! db {:delete-from :interest-tag :where [:= :id id]}))

(defn list-upcoming-reminders
  "Return tags with reminder_date within the next 30 days (or overdue)."
  [db]
  (db/exec! db {:select    [:it/id :it/entity-type :it/entity-id :it/name
                             :it/reminder-date :it/notes
                             [:co/name :company-name]
                             [:ct/name :contact-name]]
                :from      [[:interest-tag :it]]
                :left-join [[:company :co]
                             [:and [:= :it/entity-type "company"]
                                   [:= :co/id :it/entity-id]]
                             [:contact :ct]
                             [:and [:= :it/entity-type "contact"]
                                   [:= :ct/id :it/entity-id]]]
                :where     [:and
                            [:is-not :it/reminder-date nil]
                            [:<= :it/reminder-date [:raw "date('now', '+30 days')"]]]
                :order-by  [[:it/reminder-date :asc]]}))
```

- [ ] **Step 4: Write `tags/handlers.clj`**

```clojure
;; src/lite_crm/tags/handlers.clj
(ns lite-crm.tags.handlers
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
  (let [{:keys [entity-type entity-id name reminder-date notes]} (:form parameters)
        entity-id-long (parse-long (str entity-id))]
    (queries/create-tag! (:db context)
                         {:entity-type  entity-type
                          :entity-id    entity-id-long
                          :name         name
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
```

- [ ] **Step 5: Wire tags into `companies/handlers.clj` and `contacts/handlers.clj`**

In `companies/handlers.clj`, add require:
```clojure
[lite-crm.tags.handlers :as tag-handlers]
```

Update `tab-handler` `"info"` branch — pass tags fragment into `info-tab-content`:
The `info-tab-content` currently has `[:div {:id "tags-section" :class ["mt-6"]}]` as a placeholder. Update `companies/views.clj` `info-tab-content` to accept `:tags-section` and render it:

```clojure
; In companies/views.clj — update info-tab-content signature and body:
(defn info-tab-content
  [{:keys [router company addresses phones editing? errors tags-section]}]
  [:div
   (if editing?
     (info-edit-form {:router router :company company :errors errors})
     (info-display {:router router :company company}))
   (addresses-section {:router router :company company :addresses addresses})
   (phones-section {:router router :company company :phones phones})
   (or tags-section [:div {:id "tags-section" :class ["mt-6"]}])])
```

In `companies/handlers.clj`, update `tab-handler` and `update-handler` to pass tags:
```clojure
; In tab-handler "info" branch, add after existing let bindings:
tags-section (tag-handlers/tags-section-fragment (:db context) "company" id)
; Then pass :tags-section tags-section into the view call.

; Same for update-handler success path and error path.
```

In `contacts/views.clj`, update `contact-page` to render passed-in `tags-section`:
```clojure
; Replace [:div {:id "tags-section" :class ["mt-6"]}] with:
(or tags-section [:div {:id "tags-section" :class ["mt-6"]}])
```

In `contacts/handlers.clj` `detail-handler`, add:
```clojure
[lite-crm.tags.handlers :as tag-handlers]
; ...
tags-section (tag-handlers/tags-section-fragment (:db context) "contact" id)
; Pass :tags-section tags-section into view.
```

- [ ] **Step 6: Add routes to `routes.clj`**

Add require: `[lite-crm.tags.handlers :as tag-handlers]`

Add top-level route group:
```clojure
["/tags"
 {:middleware [[auth-middleware/wrap-authentication auth-backend] wrap-login-required]}
 [""  {:name ::tags
       :post {:handler    tag-handlers/create-handler
              :parameters {:form [:map
                                  [:entity-type [:enum "company" "contact"]]
                                  [:entity-id   pos-int?]
                                  [:name        [:string {:min 1}]]
                                  [:reminder-date {:optional true} string?]
                                  [:notes         {:optional true} string?]]}
              :responses  {200 {:body string?}}}}]
 ["/:id"
  {:name       ::tag
   :parameters {:path [:map [:id pos-int?]]}
   :delete     {:handler    tag-handlers/delete-handler
                :parameters {:form [:map
                                    [:entity-type [:enum "company" "contact"]]
                                    [:entity-id   pos-int?]]}
                :responses  {200 {:body string?}}}}]]
```

- [ ] **Step 7: Run tests — expect PASS**

```bash
bb test
```

Expected: 2 new tag tests pass, all others still pass.

- [ ] **Step 8: Commit**

```bash
git add src/lite_crm/tags/ src/lite_crm/companies/ src/lite_crm/contacts/ \
        src/lite_crm/routes.clj test/lite_crm/tags_test.clj
git commit -m "Add interest tags with reminder dates on companies and contacts"
```

---

## Task 10: Dashboard

**Files:**
- Modify: `src/lite_crm/handlers.clj` — update `home-handler`
- Modify: `src/lite_crm/views.clj` — replace placeholder home-page with dashboard
- Modify: `src/lite_crm/logs/queries.clj` — add `list-recent-logs`
- Modify: `src/lite_crm/routes.clj` — ensure home requires auth

No new test file. Verify manually or add a smoke test to the existing suite.

- [ ] **Step 1: Add `list-recent-logs` to `logs/queries.clj`**

```clojure
(defn list-recent-logs
  "Last 20 logs across all companies, newest first."
  [db]
  (db/exec! db {:select   [:cl/id :cl/date :cl/content :cl/status :cl/is-pinned
                            [:co/id :company-id] [:co/name :company-name]
                            [[:group-concat :c/name] :contact-names]]
                :from     [[:contact-log :cl]]
                :join     [[:company :co] [:= :co/id :cl/company-id]]
                :left-join [[:log-contact :lc] [:= :lc/log-id :cl/id]
                             [:contact :c]     [:= :c/id :lc/contact-id]]
                :group-by [:cl/id]
                :order-by [[:cl/date :desc]]
                :limit    20}))
```

- [ ] **Step 2: Update `handlers.clj` home-handler**

Add requires:
```clojure
[lite-crm.logs.queries :as log-queries]
[lite-crm.tags.queries :as tag-queries]
```

Replace the existing `home-handler`:
```clojure
(defn home-handler
  [{:keys [context]
    user   :identity
    router :reitit.core/router}]
  (let [reminders   (tag-queries/list-upcoming-reminders (:db context))
        recent-logs (log-queries/list-recent-logs (:db context))]
    (-> {:user user :router router
         :reminders reminders :recent-logs recent-logs}
        (views/home-page)
        (ext/render-html))))
```

- [ ] **Step 3: Update `views.clj` home-page**

Replace the existing `home-page` (which is likely a simple placeholder) with:

```clojure
(defn- reminder-urgency-badge [reminder-date]
  (let [today     (.toString (java.time.LocalDate/now))
        overdue?  (neg? (compare reminder-date today))]
    (if overdue?
      [:span {:class ["inline-flex" "items-center" "px-2" "py-0.5" "rounded" "text-xs"
                      "font-medium" "bg-red-100" "text-red-700"]} "逾期"]
      [:span {:class ["inline-flex" "items-center" "px-2" "py-0.5" "rounded" "text-xs"
                      "font-medium" "bg-yellow-100" "text-yellow-700"]} "即將到期"])))

(defn home-page
  [{:keys [user router reminders recent-logs] :as data}]
  (base/layout data
    [:div
     [:h1 {:class ["text-2xl" "font-bold" "text-gray-800" "mb-6"]} "儀表板"]
     [:div {:class ["grid" "grid-cols-2" "gap-6"]}
      ; Reminders panel
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
      ; Recent logs panel
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
         [:p {:class ["text-sm" "text-gray-400" "text-center" "py-4"]} "尚無記錄"])]]]))
```

- [ ] **Step 4: Ensure home route requires auth in `routes.clj`**

The home route `["/" ...]` should be inside a route group with `wrap-login-required`. If it is currently outside of auth middleware, move it. The route should redirect unauthenticated users to `/login`. Verify the existing route definition and add middleware if needed:

```clojure
["/"
 {:middleware [[auth-middleware/wrap-authentication auth-backend] wrap-login-required]}
 ["" {:name ::home
      :get  {:handler home-handlers/home-handler
             :responses {200 {:body string?}}}}]]
```

- [ ] **Step 5: Run smoke test**

```bash
bb test
```

Expected: all existing tests pass. Then visit `http://localhost:8000` in a browser after `(reset)` in the REPL — confirm dashboard shows two panels.

- [ ] **Step 6: Commit**

```bash
git add src/lite_crm/handlers.clj src/lite_crm/views.clj \
        src/lite_crm/logs/queries.clj src/lite_crm/routes.clj
git commit -m "Add dashboard with reminders and recent logs panels"
```

---

## Task 11: Global Logs Ledger

**Files:**
- Modify: `src/lite_crm/logs/queries.clj` — add `list-all-logs`
- Modify: `src/lite_crm/logs/handlers.clj` — add `ledger-handler`
- Modify: `src/lite_crm/logs/views.clj` — add `ledger-page`
- Modify: `src/lite_crm/routes.clj` — add `GET /logs`

- [ ] **Step 1: Add `list-all-logs` to `logs/queries.clj`**

```clojure
(defn list-all-logs
  "Return all logs with optional filters."
  [db {:keys [status company-name contact-name date-from date-to pinned-only?]}]
  (let [wheres (cond-> []
                 status       (conj [:= :cl/status status])
                 company-name (conj [:like :co/name (str "%" company-name "%")])
                 contact-name (conj [:like :c/name (str "%" contact-name "%")])
                 date-from    (conj [:>= :cl/date date-from])
                 date-to      (conj [:<= :cl/date date-to])
                 pinned-only? (conj [:= :cl/is-pinned 1]))
        query  {:select    [:cl/id :cl/date :cl/content :cl/status :cl/is-pinned
                             [:co/id :company-id] [:co/name :company-name]
                             [[:group-concat [:distinct :c/name]] :contact-names]]
                :from      [[:contact-log :cl]]
                :join      [[:company :co] [:= :co/id :cl/company-id]]
                :left-join [[:log-contact :lc] [:= :lc/log-id :cl/id]
                             [:contact :c]     [:= :c/id :lc/contact-id]]
                :group-by  [:cl/id]
                :order-by  [[:cl/is-pinned :desc] [:cl/date :desc]]}
        query  (if (seq wheres) (assoc query :where (into [:and] wheres)) query)]
    (db/exec! db query)))
```

- [ ] **Step 2: Add `ledger-page` to `logs/views.clj`**

Add `[lite-crm.views.base :as base]` to `logs/views.clj` requires.

```clojure
(defn- status-options []
  [["" "（全部狀態）"]
   ["no_answer"        "未接"]
   ["answered_no_talk" "接通沒談"]
   ["sent_intro"       "寄送自介信"]
   ["appointment_set"  "已約訪"]
   ["visited"          "已拜訪"]
   ["closed"           "成交"]
   ["other"            "其他"]])

(defn ledger-page
  [{:keys [user router logs filters] :as data}]
  (base/layout data
    [:div
     [:h1 {:class ["text-2xl" "font-bold" "text-gray-800" "mb-6"]} "聯絡記錄總覽"]
     ; Filter bar
     [:form {:class ["bg-white" "rounded-lg" "shadow" "p-4" "mb-6"
                     "flex" "gap-3" "items-end" "flex-wrap"]
             :method "get" :action "/logs"}
      [:div
       [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "狀態"]
       [:select {:class ["border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
                 :name "status"}
        (for [[v l] (status-options)]
          [:option {:value v :selected (= v (:status filters))} l])]]
      [:div
       [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "公司"]
       [:input {:class ["border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
                :type "text" :name "company-name" :placeholder "公司名稱"
                :value (or (:company-name filters) "")}]]
      [:div
       [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "聯絡人"]
       [:input {:class ["border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
                :type "text" :name "contact-name" :placeholder "姓名"
                :value (or (:contact-name filters) "")}]]
      [:div
       [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "開始日期"]
       [:input {:class ["border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
                :type "date" :name "date-from" :value (or (:date-from filters) "")}]]
      [:div
       [:label {:class ["block" "text-xs" "text-gray-500" "mb-1"]} "結束日期"]
       [:input {:class ["border" "border-gray-300" "rounded" "px-2" "py-1" "text-sm"]
                :type "date" :name "date-to" :value (or (:date-to filters) "")}]]
      [:div {:class ["flex" "items-center" "gap-1" "pb-1"]}
       [:input {:type "checkbox" :name "pinned-only" :value "true" :id "pinned-only"
                :checked (= "true" (:pinned-only filters))}]
       [:label {:class ["text-sm" "text-gray-600"] :for "pinned-only"} "只看置頂"]]
      [:button {:class ["bg-indigo-600" "text-white" "px-3" "py-1.5" "rounded" "text-sm"
                        "hover:bg-indigo-700"] :type "submit"} "篩選"]
      [:a {:class ["text-sm" "text-gray-400" "hover:text-gray-600" "pb-1"] :href "/logs"} "清除"]]
     ; Log table
     [:div {:class ["bg-white" "rounded-lg" "shadow" "overflow-hidden"]}
      [:table {:class ["min-w-full"]}
       [:thead {:class ["bg-gray-50" "border-b" "border-gray-200"]}
        [:tr
         (for [h ["日期" "公司" "聯絡人" "狀態" "內容" "置頂" ""]]
           [:th {:class ["px-4" "py-3" "text-left" "text-xs" "font-medium"
                         "text-gray-500" "uppercase" "tracking-wide"]} h])]]
       [:tbody {:class ["divide-y" "divide-gray-100"]}
        (if (seq logs)
          (for [log logs]
            [:tr {:class ["hover:bg-gray-50"]}
             [:td {:class ["px-4" "py-3" "text-sm" "text-gray-600" "whitespace-nowrap"]}
              (:date log)]
             [:td {:class ["px-4" "py-3" "text-sm"]}
              [:a {:class ["text-indigo-600" "hover:underline"]
                   :href (str "/companies/" (:company-id log))}
               (:company-name log)]]
             [:td {:class ["px-4" "py-3" "text-sm" "text-gray-600"]}
              (or (:contact-names log) "—")]
             [:td {:class ["px-4" "py-3"]}
              (when (:status log) (status-badge (:status log)))]
             [:td {:class ["px-4" "py-3" "text-sm" "text-gray-700" "max-w-xs" "truncate"]}
              (:content log)]
             [:td {:class ["px-4" "py-3" "text-center"]}
              (when (pos? (:is-pinned log)) [:span {:class ["text-orange-500"]} "📌"])]
             [:td {:class ["px-4" "py-3" "text-right"]}
              [:a {:class ["text-xs" "text-gray-400" "hover:text-indigo-600"]
                   :href (str "/companies/" (:company-id log) "#logs")}
               "查看"]]])
          [:tr [:td {:class ["px-4" "py-8" "text-center" "text-gray-400"] :colspan 7}
                "尚無記錄"]])]]]]))
```

- [ ] **Step 3: Add `ledger-handler` to `logs/handlers.clj`**

```clojure
(defn ledger-handler
  "GET /logs — global filterable log ledger."
  [{:keys [context query-params]
    user   :identity
    router :reitit.core/router}]
  (let [filters {:status       (not-empty (:status query-params))
                 :company-name (not-empty (:company-name query-params))
                 :contact-name (not-empty (:contact-name query-params))
                 :date-from    (not-empty (:date-from query-params))
                 :date-to      (not-empty (:date-to query-params))
                 :pinned-only? (= "true" (:pinned-only query-params))}
        logs    (queries/list-all-logs (:db context) filters)]
    (-> {:user user :router router :logs logs :filters query-params}
        (views/ledger-page)
        (ext/render-html))))
```

- [ ] **Step 4: Add `GET /logs` route to `routes.clj`**

The `/logs` group already exists with `/:id PATCH` and `DELETE`. Add the root `GET`:

```clojure
[""  {:name ::logs
      :get  {:handler log-handlers/ledger-handler
             :responses {200 {:body string?}}}}]
```

Insert this inside the existing `/logs` route group, before `["/:id" ...]`.

- [ ] **Step 5: Run tests — expect PASS**

```bash
bb test
```

Expected: all existing tests pass. Then manually visit `http://localhost:8000/logs` to confirm the ledger page loads with filters.

- [ ] **Step 6: Commit**

```bash
git add src/lite_crm/logs/ src/lite_crm/routes.clj
git commit -m "Add global logs ledger with status/company/contact/date filters"
```

---

**Plan 3 complete. Continue with Plan 4 (CSV Import, vCard Export & Homepage Wiring)?**
