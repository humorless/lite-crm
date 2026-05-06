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
