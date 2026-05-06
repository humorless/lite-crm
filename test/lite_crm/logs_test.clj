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
