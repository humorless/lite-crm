(ns lite-crm.home-test
  (:require [clj-http.client :as http]
            [clojure.test :refer :all]
            [hickory.select :as select]
            [integrant-extras.tests :as ig-extras]
            [lite-crm.auth.queries :as queries]
            [lite-crm.test-utils :as test-utils]
            [reitit-extras.tests :as reitit-extras]))

(use-fixtures :once
  (ig-extras/with-system))

(use-fixtures :each
  test-utils/with-truncated-tables)

(deftest test-home-page-is-loaded-correctly
  (let [url  (reitit-extras/get-server-url (test-utils/server) :host)
        user (queries/create-user! (test-utils/db) {:email "u@t.com" :password "password123"})
        body (test-utils/response->hickory
               (http/get url {:cookies (test-utils/auth-cookies user)}))]
    (is (= "儀表板"
           (->> body
                (select/select (select/tag :h1))
                first :content first)))))
