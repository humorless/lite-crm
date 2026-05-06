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
