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
        response (http/post url {:redirect-strategy :none
                                 :cookies     (utils/auth-cookies-with-csrf user)
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
