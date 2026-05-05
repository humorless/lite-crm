(ns lite-crm.test-utils
  "Test helpers: fixture for table cleanup and response parsing."
  (:require [hickory.core :as hickory]
            [integrant-extras.tests :as ig-extras]
            [lite-crm.db :as db]
            [lite-crm.server :as server]))

(def ^:const TEST-CSRF-TOKEN "test-csrf-token")
(def ^:const TEST-SECRET-KEY "test-secret-key")

(defn with-truncated-tables
  "Delete all rows from user-managed tables between tests."
  [f]
  (let [db (::db/db ig-extras/*test-system*)]
    (db/exec! db {:delete-from :user})
    (f)))

(defn response->hickory
  "Convert a Ring response body to a Hickory document."
  [response]
  (-> response
      :body
      (hickory/parse)
      (hickory/as-hickory)))

(defn db
  "Get the database connection from the test system."
  []
  (::db/db ig-extras/*test-system*))

(defn server
  "Get the server instance from the test system."
  []
  (::server/server ig-extras/*test-system*))
