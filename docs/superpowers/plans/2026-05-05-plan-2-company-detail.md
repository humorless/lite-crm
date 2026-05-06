# Lite CRM — Plan 2: Company Detail

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the tabbed company detail page with Info editing, Addresses/Phones management, and a full contact-log tab with pin/unpin.

**Architecture:** Company detail has a tab shell rendered server-side; tab content is swapped via HTMX `hx-get`. Alpine.js manages active-tab button styling. The Info tab uses pure HTMX get/patch for edit mode. Logs list is re-rendered in full on create/pin/delete so ordering (pinned-first) is always correct.

**Tech Stack:** Same as Plan 1. Requires Plan 1 to be complete.

---

## File Structure

**Create:**
- `src/lite_crm/logs/queries.clj`
- `src/lite_crm/logs/handlers.clj`
- `src/lite_crm/logs/views.clj`
- `test/lite_crm/logs_test.clj`

**Modify:**
- `src/lite_crm/companies/queries.clj` — add get, update, address/phone CRUD
- `src/lite_crm/companies/handlers.clj` — add detail, update, tab, address/phone handlers
- `src/lite_crm/companies/views.clj` — add company-page, tab-nav, info/address/phone/logs views
- `src/lite_crm/routes.clj` — add company detail + log routes
- `test/lite_crm/companies_test.clj` — add detail + address/phone tests

---

## Task 4: Company Detail Shell + Info Tab

**Files:**
- Modify: `src/lite_crm/companies/queries.clj`
- Modify: `src/lite_crm/companies/handlers.clj`
- Modify: `src/lite_crm/companies/views.clj`
- Modify: `src/lite_crm/routes.clj`
- Modify: `test/lite_crm/companies_test.clj`

- [ ] **Step 1: Write failing tests**

Append to `test/lite_crm/companies_test.clj`:

```clojure
(deftest test-company-detail-requires-auth
  (let [company  (company-queries/create-company! (utils/db) {:name "測試公司" :tier "no_plan"})
        url      (str (reitit-extras/get-server-url (utils/server)) "/companies/" (:id company))
        response (http/get url {:redirect-strategy :none})]
    (is (= 302 (:status response)))))

(deftest test-company-detail-shows-name
  (let [user    (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        company (company-queries/create-company! (utils/db) {:name "台積電" :tier "has_plan"})
        url     (str (reitit-extras/get-server-url (utils/server)) "/companies/" (:id company))
        body    (utils/response->hickory (http/get url {:cookies (utils/auth-cookies user)}))]
    (is (some #(= ["台積電"] (:content %))
              (select/select (select/tag :h1) body)))))

(deftest test-update-company-info
  (let [user    (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        company (company-queries/create-company! (utils/db) {:name "舊名字" :tier "no_plan"})
        url     (str (reitit-extras/get-server-url (utils/server)) "/companies/" (:id company))
        response (http/patch url {:cookies     (utils/auth-cookies-with-csrf user)
                                  :form-params {reitit-extras/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                                :name "新名字" :tier "has_plan"}})]
    (is (= 200 (:status response)))
    (is (= "新名字" (:name (company-queries/get-company (utils/db) (:id company)))))))
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
bb test
```

Expected: `company-queries/get-company` not found.

- [ ] **Step 3: Add queries to `companies/queries.clj`**

```clojure
(defn get-company
  "Return a single company by id, or nil."
  [db id]
  (db/exec-one! db {:select [:*] :from [:company] :where [:= :id id]}))

(defn update-company!
  "Update company fields. Returns updated row."
  [db id {:keys [name industry tier notes]}]
  (db/exec-one! db {:update    :company
                    :set       (cond-> {:updated-at [:raw "CURRENT_TIMESTAMP"]}
                                 name     (assoc :name name)
                                 industry (assoc :industry industry)
                                 tier     (assoc :tier tier)
                                 notes    (assoc :notes notes))
                    :where     [:= :id id]
                    :returning [:*]}))

(defn list-addresses
  [db company-id]
  (db/exec! db {:select [:*] :from [:company-address]
                :where [:= :company-id company-id]
                :order-by [[:is-primary :desc] [:id :asc]]}))

(defn list-phones
  [db company-id]
  (db/exec! db {:select [:*] :from [:company-phone]
                :where [:= :company-id company-id]
                :order-by [[:is-primary :desc] [:id :asc]]}))
```

- [ ] **Step 4: Add views to `companies/views.clj`**

Add the following new functions. The `tab-nav` uses Alpine.js for active-tab styling; HTMX fetches each tab's content fragment.

```clojure
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
      (for [[val label] tier-labels]
        [:option {:value val :selected (= val (:tier company))} label])]]
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
  [{:keys [router company addresses phones editing? errors]}]
  [:div
   (if editing?
     (info-edit-form {:router router :company company :errors errors})
     (info-display {:router router :company company}))
   ; addresses/phones added in Task 5
   [:div {:id "addresses-section" :class ["mt-6"]}]
   [:div {:id "phones-section" :class ["mt-4"]}]
   ; tags added in Plan 3
   [:div {:id "tags-section" :class ["mt-6"]}]])

(defn company-page
  "Full page: company detail with tab shell."
  [{:keys [user router company addresses phones] :as data}]
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
```

- [ ] **Step 5: Add handlers to `companies/handlers.clj`**

Add requires at top of ns:
```clojure
[ring.util.response :as response]
```

Append functions:

```clojure
(defn detail-handler
  [{:keys [context parameters]
    user   :identity
    router :reitit.core/router}]
  (let [id      (get-in parameters [:path :id])
        company (queries/get-company (:db context) id)]
    (if (nil? company)
      (response/not-found "Company not found")
      (let [addresses (queries/list-addresses (:db context) id)
            phones    (queries/list-phones (:db context) id)]
        (-> {:user user :router router :company company
             :addresses addresses :phones phones}
            (views/company-page)
            (ext/render-html))))))

(defn tab-handler
  "Returns tab content fragment for HTMX tab switching."
  [{:keys [context parameters query-params]
    user   :identity
    router :reitit.core/router}]
  (let [id      (get-in parameters [:path :id])
        tab     (get-in parameters [:path :tab])
        company (queries/get-company (:db context) id)]
    (case tab
      "info"
      (let [addresses (queries/list-addresses (:db context) id)
            phones    (queries/list-phones (:db context) id)
            editing?  (= "true" (:editing query-params))]
        (-> {:router router :company company
             :addresses addresses :phones phones :editing? editing?}
            (views/info-tab-content)
            (ext/render-html)))
      ; contacts and logs tabs wired in Plan 3 and Task 6
      (ext/render-html [:div {:class ["py-8" "text-center" "text-gray-400"]} "即將推出"]))))

(defn update-handler
  "PATCH /companies/:id — updates info fields, returns info tab content."
  [{:keys [context errors parameters params]
    user   :identity
    router :reitit.core/router}]
  (let [id (get-in parameters [:path :id])]
    (if (some? errors)
      (let [company   (queries/get-company (:db context) id)
            addresses (queries/list-addresses (:db context) id)
            phones    (queries/list-phones (:db context) id)]
        (-> {:router router :company (merge company (:form parameters))
             :addresses addresses :phones phones :editing? true :errors (:humanized errors)}
            (views/info-tab-content)
            (ext/render-html)))
      (let [{:keys [name industry tier notes]} (:form parameters)]
        (queries/update-company! (:db context) id {:name name :industry industry
                                                    :tier tier :notes notes})
        (let [company   (queries/get-company (:db context) id)
              addresses (queries/list-addresses (:db context) id)
              phones    (queries/list-phones (:db context) id)]
          (-> {:router router :company company
               :addresses addresses :phones phones :editing? false}
              (views/info-tab-content)
              (ext/render-html)))))))
```

- [ ] **Step 6: Add routes to `routes.clj`**

Inside the `/companies` route group (after `/new`):

```clojure
["/:id"
 {:parameters {:path [:map [:id pos-int?]]}}
 ["" {:name  ::company
      :get   {:handler   company-handlers/detail-handler
              :responses {200 {:body string?}}}
      :patch {:handler    company-handlers/update-handler
              :parameters {:form [:map
                                  [:name {:optional true} [:string {:min 1}]]
                                  [:industry {:optional true} string?]
                                  [:tier {:optional true} string?]
                                  [:notes {:optional true} string?]]}
              :responses  {200 {:body string?}}}}]
 ["/tabs/:tab"
  {:name       ::company-tab
   :parameters {:path [:map [:id pos-int?] [:tab string?]]}
   :get        {:handler   company-handlers/tab-handler
                :responses {200 {:body string?}}}}]]
```

- [ ] **Step 7: Run tests — expect PASS**

```bash
bb test
```

Expected: 3 new company-detail tests pass.

- [ ] **Step 8: Commit**

```bash
git add src/lite_crm/companies/ src/lite_crm/routes.clj test/lite_crm/companies_test.clj
git commit -m "Add company detail page with tabbed Info tab"
```

---

## Task 5: Addresses + Phones

**Files:**
- Modify: `src/lite_crm/companies/queries.clj`
- Modify: `src/lite_crm/companies/handlers.clj`
- Modify: `src/lite_crm/companies/views.clj`
- Modify: `src/lite_crm/routes.clj`
- Modify: `test/lite_crm/companies_test.clj`

- [ ] **Step 1: Write failing tests**

Append to `test/lite_crm/companies_test.clj`:

```clojure
(deftest test-add-address
  (let [user    (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        company (company-queries/create-company! (utils/db) {:name "台積電" :tier "no_plan"})
        url     (str (reitit-extras/get-server-url (utils/server))
                     "/companies/" (:id company) "/addresses")
        response (http/post url {:cookies     (utils/auth-cookies-with-csrf user)
                                 :form-params {reitit-extras/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :address "新竹科學園區"
                                               :label "總部"}})]
    (is (= 200 (:status response)))
    (is (= 1 (count (company-queries/list-addresses (utils/db) (:id company)))))))

(deftest test-delete-address
  (let [user    (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        company (company-queries/create-company! (utils/db) {:name "台積電" :tier "no_plan"})
        addr    (company-queries/create-address! (utils/db) {:company-id (:id company)
                                                              :address "新竹科學園區"})
        url     (str (reitit-extras/get-server-url (utils/server))
                     "/companies/" (:id company) "/addresses/" (:id addr))
        response (http/delete url {:cookies (utils/auth-cookies-with-csrf user)
                                   :form-params {reitit-extras/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN}})]
    (is (= 200 (:status response)))
    (is (zero? (count (company-queries/list-addresses (utils/db) (:id company)))))))
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
bb test
```

Expected: `company-queries/create-address!` not found.

- [ ] **Step 3: Add address/phone queries to `companies/queries.clj`**

```clojure
(defn create-address!
  [db {:keys [company-id label address is-primary]}]
  (db/exec-one! db {:insert-into :company-address
                    :values      [{:company-id company-id
                                   :label      label
                                   :address    address
                                   :is-primary (if is-primary 1 0)}]
                    :returning   [:*]}))

(defn delete-address!
  [db id]
  (db/exec-one! db {:delete-from :company-address :where [:= :id id]}))

(defn create-phone!
  [db {:keys [company-id label phone is-primary]}]
  (db/exec-one! db {:insert-into :company-phone
                    :values      [{:company-id company-id
                                   :label      label
                                   :phone      phone
                                   :is-primary (if is-primary 1 0)}]
                    :returning   [:*]}))

(defn delete-phone!
  [db id]
  (db/exec-one! db {:delete-from :company-phone :where [:= :id id]}))
```

- [ ] **Step 4: Add address/phone view fragments to `companies/views.clj`**

Add these functions (replace the empty `[:div {:id "addresses-section"}]` in `info-tab-content` with calls to these):

```clojure
(defn addresses-section
  "Renders address list + add form. Used as HTMX fragment target."
  [{:keys [router company addresses]}]
  (let [id (:id company)]
    [:div {:id "addresses-section" :class ["mt-6"]}
     [:h3 {:class ["text-sm" "font-semibold" "text-gray-600" "mb-2"]} "地址"]
     [:ul {:class ["space-y-1" "mb-3"]}
      (for [a addresses]
        [:li {:class ["flex" "items-center" "justify-between" "text-sm"]}
         [:span (when (:label a) [:span {:class ["text-gray-400" "mr-2"]} (:label a)])
          (:address a)]
         [:button {:class ["text-red-400" "hover:text-red-600" "text-xs" "ml-2"]
                   :hx-delete (str (ext/get-route router ::routes/companies)
                                   "/" id "/addresses/" (:id a))
                   :hx-headers (ext/csrf-token-json)
                   :hx-target "#addresses-section"
                   :hx-swap "outerHTML"
                   :hx-confirm "確定刪除此地址？"} "移除"]])]
     [:form {:class ["flex" "gap-2" "items-end"]
             :hx-post (str (ext/get-route router ::routes/companies) "/" id "/addresses")
             :hx-target "#addresses-section"
             :hx-swap "outerHTML"}
      (ext/csrf-token-html)
      [:input {:class ["border" "border-gray-300" "rounded" "px-2" "py-1" "text-xs" "w-20"]
               :type "text" :name "label" :placeholder "標籤"}]
      [:input {:class ["border" "border-gray-300" "rounded" "px-2" "py-1" "text-xs" "flex-1"]
               :type "text" :name "address" :placeholder "地址" :required true}]
      [:button {:class ["bg-gray-100" "text-gray-700" "px-2" "py-1" "rounded" "text-xs"
                        "hover:bg-gray-200"] :type "submit"} "新增"]]]))

(defn phones-section
  "Renders phone list + add form."
  [{:keys [router company phones]}]
  (let [id (:id company)]
    [:div {:id "phones-section" :class ["mt-4"]}
     [:h3 {:class ["text-sm" "font-semibold" "text-gray-600" "mb-2"]} "電話"}]
     [:ul {:class ["space-y-1" "mb-3"]}
      (for [p phones]
        [:li {:class ["flex" "items-center" "justify-between" "text-sm"]}
         [:span (when (:label p) [:span {:class ["text-gray-400" "mr-2"]} (:label p)])
          (:phone p)]
         [:button {:class ["text-red-400" "hover:text-red-600" "text-xs" "ml-2"]
                   :hx-delete (str (ext/get-route router ::routes/companies)
                                   "/" id "/phones/" (:id p))
                   :hx-headers (ext/csrf-token-json)
                   :hx-target "#phones-section"
                   :hx-swap "outerHTML"
                   :hx-confirm "確定刪除此電話？"} "移除"]])]
     [:form {:class ["flex" "gap-2" "items-end"]
             :hx-post (str (ext/get-route router ::routes/companies) "/" id "/phones")
             :hx-target "#phones-section"
             :hx-swap "outerHTML"}
      (ext/csrf-token-html)
      [:input {:class ["border" "border-gray-300" "rounded" "px-2" "py-1" "text-xs" "w-20"]
               :type "text" :name "label" :placeholder "標籤"}]
      [:input {:class ["border" "border-gray-300" "rounded" "px-2" "py-1" "text-xs" "flex-1"]
               :type "text" :name "phone" :placeholder "電話" :required true}]
      [:button {:class ["bg-gray-100" "text-gray-700" "px-2" "py-1" "rounded" "text-xs"
                        "hover:bg-gray-200"] :type "submit"} "新增"]]]))
```

Update `info-tab-content` to call these:

```clojure
(defn info-tab-content
  [{:keys [router company addresses phones editing? errors]}]
  [:div
   (if editing?
     (info-edit-form {:router router :company company :errors errors})
     (info-display {:router router :company company}))
   (addresses-section {:router router :company company :addresses addresses})
   (phones-section {:router router :company company :phones phones})
   [:div {:id "tags-section" :class ["mt-6"]}]])
```

- [ ] **Step 5: Add address/phone handlers to `companies/handlers.clj`**

```clojure
(defn create-address-handler
  [{:keys [context parameters]
    router :reitit.core/router}]
  (let [id   (get-in parameters [:path :id])
        {:keys [label address]} (:form parameters)]
    (queries/create-address! (:db context) {:company-id id :label label :address address})
    (let [addresses (queries/list-addresses (:db context) id)
          company   (queries/get-company (:db context) id)]
      (-> {:router router :company company :addresses addresses}
          (views/addresses-section)
          (ext/render-html)))))

(defn delete-address-handler
  [{:keys [context parameters]
    router :reitit.core/router}]
  (let [id      (get-in parameters [:path :id])
        addr-id (get-in parameters [:path :addr-id])]
    (queries/delete-address! (:db context) addr-id)
    (let [addresses (queries/list-addresses (:db context) id)
          company   (queries/get-company (:db context) id)]
      (-> {:router router :company company :addresses addresses}
          (views/addresses-section)
          (ext/render-html)))))

(defn create-phone-handler
  [{:keys [context parameters]
    router :reitit.core/router}]
  (let [id  (get-in parameters [:path :id])
        {:keys [label phone]} (:form parameters)]
    (queries/create-phone! (:db context) {:company-id id :label label :phone phone})
    (let [phones  (queries/list-phones (:db context) id)
          company (queries/get-company (:db context) id)]
      (-> {:router router :company company :phones phones}
          (views/phones-section)
          (ext/render-html)))))

(defn delete-phone-handler
  [{:keys [context parameters]
    router :reitit.core/router}]
  (let [id       (get-in parameters [:path :id])
        phone-id (get-in parameters [:path :phone-id])]
    (queries/delete-phone! (:db context) phone-id)
    (let [phones  (queries/list-phones (:db context) id)
          company (queries/get-company (:db context) id)]
      (-> {:router router :company company :phones phones}
          (views/phones-section)
          (ext/render-html)))))
```

- [ ] **Step 6: Add routes to `routes.clj`** (inside `/:id` group)

```clojure
["/addresses"
 {:name       ::company-addresses
  :parameters {:path [:map [:id pos-int?]]}
  :post       {:handler    company-handlers/create-address-handler
               :parameters {:form [:map
                                   [:address [:string {:min 1}]]
                                   [:label {:optional true} string?]]}
               :responses  {200 {:body string?}}}}]
["/addresses/:addr-id"
 {:name       ::company-address
  :parameters {:path [:map [:id pos-int?] [:addr-id pos-int?]]}
  :delete     {:handler   company-handlers/delete-address-handler
               :responses {200 {:body string?}}}}]
["/phones"
 {:name       ::company-phones
  :parameters {:path [:map [:id pos-int?]]}
  :post       {:handler    company-handlers/create-phone-handler
               :parameters {:form [:map
                                   [:phone [:string {:min 1}]]
                                   [:label {:optional true} string?]]}
               :responses  {200 {:body string?}}}}]
["/phones/:phone-id"
 {:name       ::company-phone
  :parameters {:path [:map [:id pos-int?] [:phone-id pos-int?]]}
  :delete     {:handler   company-handlers/delete-phone-handler
               :responses {200 {:body string?}}}}]
```

- [ ] **Step 7: Run tests — expect PASS**

```bash
bb test
```

Expected: all tests pass including 2 new address tests.

- [ ] **Step 8: Commit**

```bash
git add src/lite_crm/companies/ src/lite_crm/routes.clj test/lite_crm/companies_test.clj
git commit -m "Add company address and phone management"
```

---

## Task 6: Logs Tab

**Files:**
- Create: `src/lite_crm/logs/queries.clj`
- Create: `src/lite_crm/logs/handlers.clj`
- Create: `src/lite_crm/logs/views.clj`
- Create: `test/lite_crm/logs_test.clj`
- Modify: `src/lite_crm/companies/handlers.clj` — wire logs into tab-handler
- Modify: `src/lite_crm/routes.clj`

- [ ] **Step 1: Write failing tests**

```clojure
;; test/lite_crm/logs_test.clj
(ns lite-crm.logs-test
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [integrant-extras.tests :as ig-extras]
            [lite-crm.auth.queries :as auth-queries]
            [lite-crm.companies.queries :as company-queries]
            [lite-crm.logs.queries :as log-queries]
            [lite-crm.test-utils :as utils]
            [reitit-extras.tests :as reitit-extras]))

(use-fixtures :once (ig-extras/with-system))
(use-fixtures :each utils/with-truncated-tables)

(deftest test-create-log
  (let [user    (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        company (company-queries/create-company! (utils/db) {:name "台積電" :tier "no_plan"})
        url     (str (reitit-extras/get-server-url (utils/server))
                     "/companies/" (:id company) "/logs")
        response (http/post url {:cookies     (utils/auth-cookies-with-csrf user)
                                 :form-params {reitit-extras/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                               :date    "2026-05-01"
                                               :content "初次聯絡，客戶有興趣"
                                               :status  "answered_no_talk"}})]
    (is (= 200 (:status response)))
    (is (= 1 (count (log-queries/list-logs-by-company (utils/db) (:id company)))))))

(deftest test-log-tab-fragment
  (let [user    (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        company (company-queries/create-company! (utils/db) {:name "台積電" :tier "no_plan"})
        _log    (log-queries/create-log! (utils/db)
                                         {:company-id (:id company)
                                          :date "2026-05-01"
                                          :content "test log"
                                          :status "no_answer"} [])
        url     (str (reitit-extras/get-server-url (utils/server))
                     "/companies/" (:id company) "/tabs/logs")
        response (http/get url {:cookies (utils/auth-cookies user)})]
    (is (= 200 (:status response)))
    (is (clojure.string/includes? (:body response) "test log"))))
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
bb test
```

Expected: `lite-crm.logs.queries` not found.

- [ ] **Step 3: Write `logs/queries.clj`**

```clojure
;; src/lite_crm/logs/queries.clj
(ns lite-crm.logs.queries
  "DB queries for contact logs."
  (:require [lite-crm.db :as db]))

(def ^:private status-labels
  {"no_answer"       "未接"
   "answered_no_talk" "接通沒談"
   "sent_intro"      "寄送自介信"
   "appointment_set" "已約訪"
   "visited"         "已拜訪"
   "closed"          "成交"
   "other"           "其他"})

(defn status-label [status] (get status-labels status status))

(defn list-logs-by-company
  "Return logs for a company: pinned first, then by date desc."
  [db company-id]
  (db/exec! db {:select   [:cl/id :cl/date :cl/content :cl/status :cl/is-pinned
                            :cl/created-at
                            [[:group-concat :c/name] :contact-names]]
                :from     [[:contact-log :cl]]
                :left-join [[:log-contact :lc] [:= :lc/log-id :cl/id]
                             [:contact :c]      [:= :c/id :lc/contact-id]]
                :where    [:= :cl/company-id company-id]
                :group-by [:cl/id]
                :order-by [[:cl/is-pinned :desc] [:cl/date :desc]]}))

(defn get-log
  "Return a single log row."
  [db id]
  (db/exec-one! db {:select [:*] :from [:contact-log] :where [:= :id id]}))

(defn create-log!
  "Insert a log and associate contact-ids."
  [db {:keys [company-id date content status created-by]} contact-ids]
  (let [log (db/exec-one! db {:insert-into :contact-log
                               :values      [{:company-id  company-id
                                              :date        date
                                              :content     content
                                              :status      status
                                              :created-by  created-by}]
                               :returning   [:*]})]
    (when (seq contact-ids)
      (db/exec! db {:insert-into :log-contact
                    :values      (mapv #(hash-map :log-id (:id log) :contact-id %) contact-ids)}))
    log))

(defn toggle-pin!
  "Flip is_pinned on a log."
  [db id is-pinned]
  (db/exec-one! db {:update    :contact-log
                    :set       {:is-pinned (if is-pinned 1 0)}
                    :where     [:= :id id]
                    :returning [:*]}))

(defn delete-log!
  [db id]
  (db/exec-one! db {:delete-from :contact-log :where [:= :id id]}))
```

- [ ] **Step 4: Write `logs/views.clj`**

```clojure
;; src/lite_crm/logs/views.clj
(ns lite-crm.logs.views
  "Hiccup views for contact logs."
  (:require [lite-crm.logs.queries :as queries]
            [lite-crm.routes :as-alias routes]
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
  [{:keys [log router]}]
  [:div {:class ["border-t" "border-gray-100" "py-3" "flex" "gap-3"]}
   [:div {:class ["flex-1"]}
    [:div {:class ["flex" "items-center" "gap-2" "mb-1"]}
     [:span {:class ["text-xs" "text-gray-400"]} (:date log)]
     (when (:status log) (status-badge (:status log)))
     (when (pos? (:is-pinned log))
       [:span {:class ["text-xs" "text-orange-500"]} "📌 置頂"])
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
  "Inline form for creating a new log (no contacts yet — wired in Plan 3)."
  [{:keys [router company-id contacts]}]
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
      (for [[val label] {"no_answer"       "未接"
                          "answered_no_talk" "接通沒談"
                          "sent_intro"      "寄送自介信"
                          "appointment_set" "已約訪"
                          "visited"         "已拜訪"
                          "closed"          "成交"
                          "other"           "其他"}]
        [:option {:value val} label])]]
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
  [{:keys [router company logs contacts]}]
  [:div {:x-data "{ showForm: false }"}
   [:div {:class ["flex" "justify-end" "mb-3"]}
    [:button {:class ["bg-indigo-600" "text-white" "px-3" "py-1.5" "rounded" "text-sm"
                      "hover:bg-indigo-700"] :x-on:click "showForm = true"}
     "+ 新增記錄"]]
   (add-log-form {:router router :company-id (:id company) :contacts contacts})
   (logs-list-fragment {:logs logs :router router})])
```

- [ ] **Step 5: Write `logs/handlers.clj`**

```clojure
;; src/lite_crm/logs/handlers.clj
(ns lite-crm.logs.handlers
  "HTTP handlers for contact logs."
  (:require [lite-crm.logs.queries :as queries]
            [lite-crm.logs.views :as views]
            [reitit-extras.core :as ext]))

(defn create-handler
  "POST /companies/:id/logs"
  [{:keys [context parameters]
    user   :identity
    router :reitit.core/router}]
  (let [company-id   (get-in parameters [:path :id])
        {:keys [date content status contact-ids]} (:form parameters)
        contact-ids-vec (cond
                          (nil? contact-ids)    []
                          (string? contact-ids) [(parse-long contact-ids)]
                          :else                 (mapv parse-long contact-ids))]
    (queries/create-log! (:db context)
                         {:company-id company-id
                          :date       date
                          :content    content
                          :status     status
                          :created-by (:id user)}
                         contact-ids-vec)
    (let [logs (queries/list-logs-by-company (:db context) company-id)]
      (-> {:router router :logs logs}
          (views/logs-list-fragment)
          (ext/render-html)))))
```

- [ ] **Step 6: Wire logs tab into `companies/handlers.clj` `tab-handler`**

Add requires:
```clojure
[lite-crm.logs.queries :as log-queries]
[lite-crm.logs.views :as log-views]
```

Update the `"logs"` branch in `tab-handler`:
```clojure
"logs"
(let [logs    (log-queries/list-logs-by-company (:db context) id)
      company (queries/get-company (:db context) id)]
  (-> {:router router :company company :logs logs :contacts []}
      (log-views/logs-tab-content)
      (ext/render-html)))
```

- [ ] **Step 7: Add routes to `routes.clj`**

Add `[lite-crm.logs.handlers :as log-handlers]` to requires.

Inside the `/companies /:id` group:
```clojure
["/logs"
 {:name       ::company-logs
  :parameters {:path [:map [:id pos-int?]]}
  :post       {:handler    log-handlers/create-handler
               :parameters {:form [:map
                                   [:date    [:string {:min 1}]]
                                   [:content [:string {:min 1}]]
                                   [:status        {:optional true} string?]
                                   [:contact-ids   {:optional true}
                                    [:or string? [:vector string?]]]]}
               :responses  {200 {:body string?}}}}]
```

- [ ] **Step 8: Run tests — expect PASS**

```bash
bb test
```

Expected: all 2 new log tests pass.

- [ ] **Step 9: Commit**

```bash
git add src/lite_crm/logs/ src/lite_crm/companies/handlers.clj \
        src/lite_crm/routes.clj test/lite_crm/logs_test.clj
git commit -m "Add logs tab with create-log"
```

---

## Task 7: Log Pin/Unpin + Delete

**Files:**
- Modify: `src/lite_crm/logs/handlers.clj`
- Modify: `src/lite_crm/routes.clj`
- Modify: `test/lite_crm/logs_test.clj`

- [ ] **Step 1: Write failing tests**

Append to `test/lite_crm/logs_test.clj`:

```clojure
(deftest test-pin-log
  (let [user    (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        company (company-queries/create-company! (utils/db) {:name "台積電" :tier "no_plan"})
        log     (log-queries/create-log! (utils/db)
                                         {:company-id (:id company)
                                          :date "2026-05-01" :content "test" :status nil} [])
        url     (str (reitit-extras/get-server-url (utils/server)) "/logs/" (:id log))
        response (http/patch url {:cookies     (utils/auth-cookies-with-csrf user)
                                  :form-params {reitit-extras/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                                :is-pinned "true"}})]
    (is (= 200 (:status response)))
    (is (= 1 (:is-pinned (log-queries/get-log (utils/db) (:id log)))))))

(deftest test-delete-log
  (let [user    (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        company (company-queries/create-company! (utils/db) {:name "台積電" :tier "no_plan"})
        log     (log-queries/create-log! (utils/db)
                                         {:company-id (:id company)
                                          :date "2026-05-01" :content "test" :status nil} [])
        url     (str (reitit-extras/get-server-url (utils/server)) "/logs/" (:id log))
        response (http/delete url {:cookies     (utils/auth-cookies-with-csrf user)
                                   :form-params {reitit-extras/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN}})]
    (is (= 200 (:status response)))
    (is (zero? (count (log-queries/list-logs-by-company (utils/db) (:id company)))))))
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
bb test
```

Expected: no route for `PATCH /logs/:id`.

- [ ] **Step 3: Add handlers to `logs/handlers.clj`**

```clojure
(defn update-handler
  "PATCH /logs/:id — toggle pin. Returns updated logs-list fragment."
  [{:keys [context parameters]
    router :reitit.core/router}]
  (let [id        (get-in parameters [:path :id])
        is-pinned (= "true" (get-in parameters [:form :is-pinned]))
        log       (queries/get-log (:db context) id)]
    (queries/toggle-pin! (:db context) id is-pinned)
    (let [logs (queries/list-logs-by-company (:db context) (:company-id log))]
      (-> {:router router :logs logs}
          (views/logs-list-fragment)
          (ext/render-html)))))

(defn delete-handler
  "DELETE /logs/:id — delete log. Returns updated logs-list fragment."
  [{:keys [context parameters]
    router :reitit.core/router}]
  (let [id         (get-in parameters [:path :id])
        log        (queries/get-log (:db context) id)
        company-id (:company-id log)]
    (queries/delete-log! (:db context) id)
    (let [logs (queries/list-logs-by-company (:db context) company-id)]
      (-> {:router router :logs logs}
          (views/logs-list-fragment)
          (ext/render-html)))))
```

- [ ] **Step 4: Add routes to `routes.clj`** (top-level, sibling of `/companies`)

```clojure
["/logs"
 {:middleware [[auth-middleware/wrap-authentication auth-backend]
               wrap-login-required]}
 ["/:id"
  {:name       ::log
   :parameters {:path [:map [:id pos-int?]]}
   :patch      {:handler    log-handlers/update-handler
                :parameters {:form [:map [:is-pinned {:optional true} string?]]}
                :responses  {200 {:body string?}}}
   :delete     {:handler   log-handlers/delete-handler
                :responses {200 {:body string?}}}}]]
```

- [ ] **Step 5: Run tests — expect PASS**

```bash
bb test
```

Expected: all 2 new pin/delete tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/lite_crm/logs/handlers.clj src/lite_crm/routes.clj \
        test/lite_crm/logs_test.clj
git commit -m "Add log pin/unpin and delete"
```

---

**Plan 2 complete. Continue with Plan 3 (Contacts, Tags & Dashboard).**
