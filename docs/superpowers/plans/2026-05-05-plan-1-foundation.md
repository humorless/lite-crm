# Lite CRM — Plan 1: Foundation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lay the database schema, shared layout, and the first working feature (companies list + create).

**Architecture:** Four SQL migration files create all CRM tables. The existing `views.clj` gains a `nav` + `layout` wrapper used by all authenticated pages. Companies list/create follows the existing routes → handlers → views pattern.

**Tech Stack:** SQLite + HoneySQL, Reitit + Ring, Hiccup + HTMX + TailwindCSS, clj-http integration tests.

**Prerequisite:** Plans are run in order. This plan must be complete before Plan 2.

---

## File Structure

**Create:**
- `resources/migrations/0003.up.sql`
- `resources/migrations/0004.up.sql`
- `resources/migrations/0005.up.sql`
- `resources/migrations/0006.up.sql`
- `src/lite_crm/companies/queries.clj`
- `src/lite_crm/companies/handlers.clj`
- `src/lite_crm/companies/views.clj`
- `test/lite_crm/companies_test.clj`

**Modify:**
- `src/lite_crm/views.clj` — add `nav`, `layout`; replace `home-page` placeholder
- `src/lite_crm/handlers.clj` — pass `:user` to home-handler
- `src/lite_crm/routes.clj` — add company requires + routes
- `test/lite_crm/test_utils.clj` — add `auth-cookies`, `auth-cookies-with-csrf`

---

## Task 1: DB Migrations

**Files:**
- Create: `resources/migrations/0003.up.sql`
- Create: `resources/migrations/0004.up.sql`
- Create: `resources/migrations/0005.up.sql`
- Create: `resources/migrations/0006.up.sql`

- [ ] **Step 1: Write migration 0003 (company tables)**

```sql
-- resources/migrations/0003.up.sql
CREATE TABLE IF NOT EXISTS company (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  name       TEXT    NOT NULL,
  industry   TEXT,
  tier       TEXT    NOT NULL DEFAULT 'no_plan',
  notes      TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS company_address (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  company_id INTEGER NOT NULL REFERENCES company(id) ON DELETE CASCADE,
  label      TEXT,
  address    TEXT NOT NULL,
  is_primary INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS company_phone (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  company_id INTEGER NOT NULL REFERENCES company(id) ON DELETE CASCADE,
  label      TEXT,
  phone      TEXT NOT NULL,
  is_primary INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_company_name ON company (name);
CREATE INDEX IF NOT EXISTS idx_company_tier ON company (tier);
```

- [ ] **Step 2: Write migration 0004 (contact table)**

```sql
-- resources/migrations/0004.up.sql
CREATE TABLE IF NOT EXISTS contact (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  company_id INTEGER REFERENCES company(id) ON DELETE SET NULL,
  name       TEXT NOT NULL,
  department TEXT,
  title      TEXT,
  phone      TEXT,
  phone_ext  TEXT,
  mobile     TEXT,
  email      TEXT,
  notes      TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_contact_company ON contact (company_id);
CREATE INDEX IF NOT EXISTS idx_contact_name    ON contact (name);
```

- [ ] **Step 3: Write migration 0005 (log tables)**

```sql
-- resources/migrations/0005.up.sql
CREATE TABLE IF NOT EXISTS contact_log (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  company_id INTEGER NOT NULL REFERENCES company(id) ON DELETE CASCADE,
  date       DATE    NOT NULL,
  content    TEXT    NOT NULL,
  status     TEXT,
  is_pinned  INTEGER NOT NULL DEFAULT 0,
  created_by INTEGER REFERENCES "user"(id),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS log_contact (
  log_id     INTEGER NOT NULL REFERENCES contact_log(id) ON DELETE CASCADE,
  contact_id INTEGER NOT NULL REFERENCES contact(id)    ON DELETE CASCADE,
  PRIMARY KEY (log_id, contact_id)
);

CREATE INDEX IF NOT EXISTS idx_log_company ON contact_log (company_id);
CREATE INDEX IF NOT EXISTS idx_log_date    ON contact_log (date);
```

- [ ] **Step 4: Write migration 0006 (interest_tag table)**

```sql
-- resources/migrations/0006.up.sql
CREATE TABLE IF NOT EXISTS interest_tag (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  entity_type   TEXT NOT NULL,
  entity_id     INTEGER NOT NULL,
  name          TEXT NOT NULL,
  reminder_date DATE,
  notes         TEXT,
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tag_entity   ON interest_tag (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_tag_reminder ON interest_tag (reminder_date);
```

- [ ] **Step 5: Verify migrations run**

```bash
bb test
```

Expected: all existing tests pass (migrations auto-run on system start; in-memory SQLite gets the new tables).

- [ ] **Step 6: Commit**

```bash
git add resources/migrations/
git commit -m "Add CRM database migrations (company, contact, log, tag)"
```

---

## Task 2: Shared Layout + Auth Test Helpers

**Files:**
- Modify: `src/lite_crm/views.clj`
- Modify: `src/lite_crm/handlers.clj`
- Modify: `test/lite_crm/test_utils.clj`

- [ ] **Step 1: Add `nav` and `layout` to `views.clj`**

Add the following requires to `views.clj`:
```clojure
; already present:
[lite-crm.routes :as-alias routes]
[reitit-extras.core :as ext]
```

Add these functions after the existing `button` function:

```clojure
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
  [{:keys [user router] :as context} content]
  (base
    [:div {:class ["min-h-screen" "bg-gray-50"]}
     (nav context)
     [:main {:class ["container" "mx-auto" "px-4" "py-6" "max-w-6xl"]}
      content]]))
```

Replace the existing `home-page` function body with:

```clojure
(defn home-page
  [{:keys [user router] :as context}]
  (layout context
    [:div {:class ["py-10" "text-center"]}
     [:h1 {:class ["text-2xl" "font-bold" "text-gray-700"]} "CRM 總覽"]
     [:p {:class ["text-gray-400" "mt-2" "text-sm"]} "Dashboard 即將建置"]]))
```

- [ ] **Step 2: Update `home-handler` in `handlers.clj` to pass `:user`**

```clojure
(defn home-handler
  [{router :reitit.core/router
    user   :identity
    :as    _request}]
  (-> {:user user :router router}
      (views/home-page)
      (reitit-extras/render-html)))
```

- [ ] **Step 3: Add auth helpers to `test_utils.clj`**

Add requires:
```clojure
[reitit-extras.tests :as reitit-extras]
```

Add functions:
```clojure
(defn auth-cookies
  "Session cookies with user identity — use for GET requests."
  [user]
  (reitit-extras/session-cookies
    {:identity (select-keys user [:id :email])}
    TEST-SECRET-KEY))

(defn auth-cookies-with-csrf
  "Session cookies with user identity + CSRF token — use for POST/PATCH/DELETE."
  [user]
  (reitit-extras/session-cookies
    {reitit-extras/CSRF-TOKEN-SESSION-KEY TEST-CSRF-TOKEN
     :identity (select-keys user [:id :email])}
    TEST-SECRET-KEY))
```

- [ ] **Step 4: Run tests**

```bash
bb test
```

Expected: all existing tests pass. The home page test will need updating since the h1 text changed — update `home_test.clj`:

```clojure
(deftest test-home-page-is-loaded-correctly
  (let [url  (reitit-extras/get-server-url (test-utils/server) :host)
        user (queries/create-user! (test-utils/db) {:email "u@t.com" :password "password123"})
        body (test-utils/response->hickory
               (http/get url {:cookies (test-utils/auth-cookies user)}))]
    (is (= "CRM 總覽"
           (->> body
                (select/select (select/tag :h1))
                first :content first)))))
```

- [ ] **Step 5: Commit**

```bash
git add src/lite_crm/views.clj src/lite_crm/handlers.clj \
        test/lite_crm/test_utils.clj test/lite_crm/home_test.clj
git commit -m "Add shared CRM layout with nav bar and test auth helpers"
```

---

## Task 3: Companies List + Create

**Files:**
- Create: `src/lite_crm/companies/queries.clj`
- Create: `src/lite_crm/companies/handlers.clj`
- Create: `src/lite_crm/companies/views.clj`
- Create: `test/lite_crm/companies_test.clj`
- Modify: `src/lite_crm/routes.clj`

- [ ] **Step 1: Write failing tests**

```clojure
;; test/lite_crm/companies_test.clj
(ns lite-crm.companies-test
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [hickory.select :as select]
            [integrant-extras.tests :as ig-extras]
            [lite-crm.auth.queries :as auth-queries]
            [lite-crm.companies.queries :as company-queries]
            [lite-crm.test-utils :as utils]
            [reitit-extras.tests :as reitit-extras]))

(use-fixtures :once (ig-extras/with-system))
(use-fixtures :each utils/with-truncated-tables)

(deftest test-companies-list-requires-auth
  (let [url      (str (reitit-extras/get-server-url (utils/server)) "/companies")
        response (http/get url {:redirect-strategy :none})]
    (is (= 302 (:status response)))))

(deftest test-companies-list-empty-state
  (let [user (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        url  (str (reitit-extras/get-server-url (utils/server)) "/companies")
        body (utils/response->hickory (http/get url {:cookies (utils/auth-cookies user)}))]
    (is (= "公司列表"
           (->> body (select/select (select/tag :h1)) first :content first)))))

(deftest test-create-company-success
  (let [user     (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        base-url (reitit-extras/get-server-url (utils/server))
        response (http/post (str base-url "/companies")
                            {:cookies     (utils/auth-cookies-with-csrf user)
                             :form-params {reitit-extras/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                           :name     "台積電"
                                           :industry "半導體"
                                           :tier     "has_plan"}})]
    (is (= 200 (:status response)))
    (is (= "/companies" (get (:headers response) "HX-Redirect")))
    (is (= 1 (count (company-queries/list-companies (utils/db) {}))))))

(deftest test-create-company-missing-name
  (let [user     (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        base-url (reitit-extras/get-server-url (utils/server))
        response (http/post (str base-url "/companies")
                            {:cookies     (utils/auth-cookies-with-csrf user)
                             :form-params {reitit-extras/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                           :name ""}})]
    (is (= 200 (:status response)))
    (is (nil? (get (:headers response) "HX-Redirect")))
    (is (zero? (count (company-queries/list-companies (utils/db) {}))))))
```

- [ ] **Step 2: Run tests — expect FAIL (namespace not found)**

```bash
bb test
```

Expected: compilation error on `lite-crm.companies.queries`.

- [ ] **Step 3: Write `companies/queries.clj`**

```clojure
;; src/lite_crm/companies/queries.clj
(ns lite-crm.companies.queries
  "DB queries for companies."
  (:require [lite-crm.db :as db]))

(defn list-companies
  "Return all companies with last log date.
   Filters: {:tier string, :tag-name string, :company-name string}."
  [db {:keys [tier tag-name company-name]}]
  (let [wheres (cond-> []
                 tier         (conj [:= :c/tier tier])
                 company-name (conj [:like :c/name (str "%" company-name "%")]))
        query  {:select    [:c/id :c/name :c/industry :c/tier
                            [[:max :cl/date] :last-log-date]]
                :from      [[:company :c]]
                :left-join [[:contact-log :cl] [:= :cl/company-id :c/id]]
                :group-by  [:c/id]
                :order-by  [[:c/name :asc]]}
        query  (if (seq wheres)
                 (assoc query :where (into [:and] wheres))
                 query)
        query  (if tag-name
                 (assoc query :inner-join
                        [:interest-tag
                         [:and [:= :interest-tag/entity-type "company"]
                               [:= :interest-tag/entity-id :c/id]
                               [:= :interest-tag/name tag-name]]])
                 query)]
    (db/exec! db query)))

(defn create-company!
  "Insert a new company. Returns the created row."
  [db {:keys [name industry tier notes]}]
  (db/exec-one! db {:insert-into :company
                    :values      [{:name     name
                                   :industry industry
                                   :tier     (or tier "no_plan")
                                   :notes    notes}]
                    :returning   [:*]}))
```

- [ ] **Step 4: Write `companies/views.clj`**

```clojure
;; src/lite_crm/companies/views.clj
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
     (for [[val label] tier-labels]
       [:option {:value val :selected (= val (:tier filters))} label])]
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
  [{:keys [user router] :as data}]
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
     (for [[val label] tier-labels]
       [:option {:value val :selected (= val (or (:tier values) "no_plan"))} label])]]
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
  [{:keys [user router] :as data}]
  (base/layout data
    [:div
     [:h1 {:class ["text-2xl" "font-bold" "text-gray-800" "mb-6"]} "新增公司"]
     (new-company-form data)]))
```

- [ ] **Step 5: Write `companies/handlers.clj`**

```clojure
;; src/lite_crm/companies/handlers.clj
(ns lite-crm.companies.handlers
  "HTTP handlers for companies list and create."
  (:require [lite-crm.companies.queries :as queries]
            [lite-crm.companies.views :as views]
            [lite-crm.routes :as-alias routes]
            [reitit-extras.core :as ext]
            [ring.util.response :as response]))

(defn list-handler
  [{:keys [context query-params]
    user   :identity
    router :reitit.core/router}]
  (let [filters   {:tier         (:tier query-params)
                   :tag-name     (:tag-name query-params)
                   :company-name (:company-name query-params)}
        companies (queries/list-companies (:db context) filters)]
    (-> {:user user :router router :companies companies :filters filters}
        (views/companies-page)
        (ext/render-html))))

(defn new-handler
  [{user   :identity
    router :reitit.core/router}]
  (-> {:user user :router router}
      (views/new-company-page)
      (ext/render-html)))

(defn create-handler
  [{:keys [context errors parameters params]
    user   :identity
    router :reitit.core/router}]
  (if (some? errors)
    (-> {:user user :router router :values params :errors (:humanized errors)}
        (views/new-company-form)
        (ext/render-html))
    (let [{:keys [name industry tier notes]} (:form parameters)]
      (queries/create-company! (:db context) {:name     name
                                               :industry industry
                                               :tier     tier
                                               :notes    notes})
      (-> (ext/render-html [:div])
          (response/header "HX-Redirect" (ext/get-route router ::routes/companies))))))
```

- [ ] **Step 6: Add company routes to `routes.clj`**

Add requires at the top of `routes.clj`:
```clojure
[lite-crm.companies.handlers :as company-handlers]
```

Add inside the `routes` vector (as a sibling of `/account`):
```clojure
["/companies"
 {:middleware [[auth-middleware/wrap-authentication auth-backend]
               wrap-login-required]}
 ["" {:name      ::companies
      :get       {:handler   company-handlers/list-handler
                  :responses {200 {:body string?}}}
      :post      {:handler    company-handlers/create-handler
                  :parameters {:form [:map
                                      [:name [:string {:min 1}]]
                                      [:industry {:optional true} string?]
                                      [:tier {:optional true} string?]
                                      [:notes {:optional true} string?]]}
                  :responses  {200 {:body string?}}}}]
 ["/new" {:name     ::new-company
          :get      {:handler   company-handlers/new-handler
                     :responses {200 {:body string?}}}}]]
```

- [ ] **Step 7: Run tests — expect PASS**

```bash
bb test
```

Expected: all tests including the 4 new company tests pass.

- [ ] **Step 8: Commit**

```bash
git add src/lite_crm/companies/ test/lite_crm/companies_test.clj \
        src/lite_crm/routes.clj
git commit -m "Add companies list and create"
```

---

**Plan 1 complete. Continue with Plan 2 (Company Detail).**
