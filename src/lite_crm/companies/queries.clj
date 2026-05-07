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
                            [[:max :cl/date] :last_log_date]]
                :from      [[:company :c]]
                :left-join [[:contact_log :cl] [:= :cl/company_id :c/id]]
                :group-by  [:c/id]
                :order-by  [[:c/name :asc]]}
        query  (if (seq wheres)
                 (assoc query :where (into [:and] wheres))
                 query)
        query  (if tag-name
                 (assoc query :inner-join
                        [:interest_tag
                         [:and [:= :interest_tag/entity_type "company"]
                               [:= :interest_tag/entity_id :c/id]
                               [:= :interest_tag/name tag-name]]])
                 query)]
    (db/exec! db query)))

(defn create-company!
  "Insert a new company. Returns the created row."
  [db {:keys [industry tier notes] company-name :name}]
  (db/exec-one! db {:insert-into :company
                    :values      [{:name     company-name
                                   :industry industry
                                   :tier     (or tier "no_plan")
                                   :notes    notes}]
                    :returning   [:*]}))

(defn get-company
  "Return a single company by id, or nil."
  [db id]
  (db/exec-one! db {:select [:*] :from [:company] :where [:= :id id]}))

(defn get-company-by-name
  "Return company with exact name match, or nil."
  [db company-name]
  (db/exec-one! db {:select [:id :name] :from [:company] :where [:= :name company-name]}))

(defn update-company!
  "Update company fields. Returns updated row."
  [db id {:keys [industry tier notes] company-name :name}]
  (db/exec-one! db {:update    :company
                    :set       (cond-> {:updated_at [:raw "CURRENT_TIMESTAMP"]}
                                 company-name (assoc :name company-name)
                                 industry     (assoc :industry industry)
                                 tier         (assoc :tier tier)
                                 notes        (assoc :notes notes))
                    :where     [:= :id id]
                    :returning [:*]}))

(defn list-addresses
  [db company-id]
  (db/exec! db {:select   [:*]
                :from     [:company_address]
                :where    [:= :company_id company-id]
                :order-by [[:is_primary :desc] [:id :asc]]}))

(defn list-phones
  [db company-id]
  (db/exec! db {:select   [:*]
                :from     [:company_phone]
                :where    [:= :company_id company-id]
                :order-by [[:is_primary :desc] [:id :asc]]}))

(defn create-address!
  [db {:keys [company-id label address is-primary]}]
  (db/exec-one! db {:insert-into :company_address
                    :values      [{:company_id company-id
                                   :label      label
                                   :address    address
                                   :is_primary (if is-primary 1 0)}]
                    :returning   [:*]}))

(defn delete-address!
  [db id]
  (db/exec-one! db {:delete-from :company_address :where [:= :id id]}))

(defn create-phone!
  [db {:keys [company-id label phone is-primary]}]
  (db/exec-one! db {:insert-into :company_phone
                    :values      [{:company_id company-id
                                   :label      label
                                   :phone      phone
                                   :is_primary (if is-primary 1 0)}]
                    :returning   [:*]}))

(defn delete-phone!
  [db id]
  (db/exec-one! db {:delete-from :company_phone :where [:= :id id]}))
