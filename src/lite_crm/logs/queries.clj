(ns lite-crm.logs.queries
  "DB queries for contact logs."
  (:require [lite-crm.db :as db]))

(def ^:private status-labels
  {"no_answer"        "未接"
   "answered_no_talk" "接通沒談"
   "sent_intro"       "寄送自介信"
   "appointment_set"  "已約訪"
   "visited"          "已拜訪"
   "closed"           "成交"
   "other"            "其他"})

(defn status-label [status] (get status-labels status status))

(defn list-logs-by-company
  "Return logs for a company: pinned first, then by date desc."
  [db company-id]
  (db/exec! db {:select   [:cl/id :cl/date :cl/content :cl/status :cl/is_pinned
                            :cl/created_at
                            [[:group_concat :c/name] :contact-names]]
                :from     [[:contact_log :cl]]
                :left-join [[:log_contact :lc] [:= :lc/log_id :cl/id]
                             [:contact :c]     [:= :c/id :lc/contact_id]]
                :where    [:= :cl/company_id company-id]
                :group-by [:cl/id]
                :order-by [[:cl/is_pinned :desc] [:cl/date :desc]]}))

(defn get-log
  "Return a single log row."
  [db id]
  (db/exec-one! db {:select [:*] :from [:contact_log] :where [:= :id id]}))

(defn create-log!
  "Insert a log and associate contact-ids."
  [db {:keys [company-id date content status created-by]} contact-ids]
  (let [log (db/exec-one! db {:insert-into :contact_log
                               :values      [{:company_id company-id
                                              :date       date
                                              :content    content
                                              :status     status
                                              :created_by created-by}]
                               :returning   [:*]})]
    (when (seq contact-ids)
      (db/exec! db {:insert-into :log_contact
                    :values      (mapv #(hash-map :log_id (:id log) :contact_id %) contact-ids)}))
    log))

(defn toggle-pin!
  "Flip is_pinned on a log."
  [db id is-pinned]
  (db/exec-one! db {:update    :contact_log
                    :set       {:is_pinned (if is-pinned 1 0)}
                    :where     [:= :id id]
                    :returning [:*]}))

(defn delete-log!
  [db id]
  (db/exec-one! db {:delete-from :contact_log :where [:= :id id]}))

(defn list-all-logs
  "Return all logs with optional filters."
  [db {:keys [status company-name contact-name date-from date-to pinned-only?]}]
  (let [wheres (cond-> []
                 status       (conj [:= :cl/status status])
                 company-name (conj [:like :co/name (str "%" company-name "%")])
                 contact-name (conj [:like :c/name (str "%" contact-name "%")])
                 date-from    (conj [:>= :cl/date date-from])
                 date-to      (conj [:<= :cl/date date-to])
                 pinned-only? (conj [:= :cl/is_pinned 1]))
        query  {:select    [:cl/id :cl/date :cl/content :cl/status :cl/is_pinned
                             [:co/id :company-id] [:co/name :company-name]
                             [[:group_concat [:distinct :c/name]] :contact-names]]
                :from      [[:contact_log :cl]]
                :join      [[:company :co] [:= :co/id :cl/company_id]]
                :left-join [[:log_contact :lc] [:= :lc/log_id :cl/id]
                             [:contact :c]     [:= :c/id :lc/contact_id]]
                :group-by  [:cl/id]
                :order-by  [[:cl/is_pinned :desc] [:cl/date :desc]]}
        query  (if (seq wheres) (assoc query :where (into [:and] wheres)) query)]
    (db/exec! db query)))

(defn list-recent-logs
  "Last 20 logs across all companies, newest first."
  [db]
  (db/exec! db {:select   [:cl/id :cl/date :cl/content :cl/status :cl/is_pinned
                            [:co/id :company-id] [:co/name :company-name]
                            [[:group_concat :c/name] :contact-names]]
                :from     [[:contact_log :cl]]
                :join     [[:company :co] [:= :co/id :cl/company_id]]
                :left-join [[:log_contact :lc] [:= :lc/log_id :cl/id]
                             [:contact :c]     [:= :c/id :lc/contact_id]]
                :group-by [:cl/id]
                :order-by [[:cl/date :desc]]
                :limit    20}))
