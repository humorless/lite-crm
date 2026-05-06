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
