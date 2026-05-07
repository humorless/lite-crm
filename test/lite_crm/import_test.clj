(ns lite-crm.import-test
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [integrant-extras.tests :as ig-extras]
            [lite-crm.auth.queries :as auth-queries]
            [lite-crm.companies.queries :as company-queries]
            [lite-crm.test-utils :as utils]
            [reitit-extras.tests :as reitit-extras]))

(use-fixtures :once (ig-extras/with-system))
(use-fixtures :each utils/with-truncated-tables)

(deftest test-import-page-requires-auth
  (let [url      (str (reitit-extras/get-server-url (utils/server)) "/companies/import")
        response (http/get url {:redirect-strategy :none})]
    (is (= 302 (:status response)))))

(deftest test-csv-preview
  (let [user    (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        url     (str (reitit-extras/get-server-url (utils/server)) "/companies/import")
        csv-str "公司名稱,產業,客戶等級\n台積電,半導體,has_plan\n聯發科,半導體,has_need"
        response (http/post url
                             {:cookies     (utils/auth-cookies-with-csrf user)
                              :headers     {reitit-extras/CSRF-TOKEN-HEADER utils/TEST-CSRF-TOKEN}
                              :multipart   [{:name "csv-file"
                                             :content csv-str
                                             :filename "companies.csv"
                                             :mime-type "text/csv"}]})]
    (is (= 200 (:status response)))
    (is (clojure.string/includes? (:body response) "台積電"))))

(deftest test-csv-import-confirm
  (let [user    (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        rows-json "[{\"name\":\"台積電\",\"industry\":\"半導體\",\"tier\":\"has_plan\"},{\"name\":\"聯發科\",\"industry\":\"半導體\",\"tier\":\"has_need\"}]"
        url     (str (reitit-extras/get-server-url (utils/server)) "/companies/import/confirm")
        response (http/post url
                             {:cookies     (utils/auth-cookies-with-csrf user)
                              :form-params {reitit-extras/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                            :rows-json rows-json}})]
    (is (= 200 (:status response)))
    (is (= 2 (count (company-queries/list-companies (utils/db) {}))))
    (is (clojure.string/includes? (:body response) "2 筆新增"))))

(deftest test-csv-import-skips-duplicates
  (let [user    (auth-queries/create-user! (utils/db) {:email "u@t.com" :password "password123"})
        _       (company-queries/create-company! (utils/db) {:name "台積電" :tier "no_plan"})
        rows-json "[{\"name\":\"台積電\",\"industry\":\"半導體\",\"tier\":\"has_plan\"},{\"name\":\"聯發科\",\"industry\":\"半導體\",\"tier\":\"has_need\"}]"
        url     (str (reitit-extras/get-server-url (utils/server)) "/companies/import/confirm")
        response (http/post url
                             {:cookies     (utils/auth-cookies-with-csrf user)
                              :form-params {reitit-extras/CSRF-TOKEN-FORM-KEY utils/TEST-CSRF-TOKEN
                                            :rows-json rows-json}})]
    (is (= 200 (:status response)))
    (is (= 2 (count (company-queries/list-companies (utils/db) {}))))
    (is (clojure.string/includes? (:body response) "1 筆新增"))
    (is (clojure.string/includes? (:body response) "1 筆略過"))))
