(ns lite-crm.tags.queries
  "DB queries for interest tags."
  (:require [lite-crm.db :as db]))

(defn list-tags
  "Return all tags for an entity."
  [db entity-type entity-id]
  (db/exec! db {:select   [:*]
                :from     [:interest_tag]
                :where    [:and
                           [:= :entity_type entity-type]
                           [:= :entity_id entity-id]]
                :order-by [[:created_at :asc]]}))

(defn create-tag!
  [db {:keys [entity-type entity-id reminder-date notes] tag-name :name}]
  (db/exec-one! db {:insert-into :interest_tag
                    :values      [(cond-> {:entity_type entity-type
                                           :entity_id   entity-id
                                           :name        tag-name}
                                    reminder-date (assoc :reminder_date reminder-date)
                                    notes         (assoc :notes notes))]
                    :returning   [:*]}))

(defn delete-tag!
  [db id]
  (db/exec-one! db {:delete-from :interest_tag :where [:= :id id]}))

(defn list-upcoming-reminders
  "Return tags with reminder_date within the next 30 days (or overdue)."
  [db]
  (db/exec! db {:select    [:it/id :it/entity_type :it/entity_id :it/name
                             :it/reminder_date :it/notes
                             [:co/name :company-name]
                             [:ct/name :contact-name]]
                :from      [[:interest_tag :it]]
                :left-join [[:company :co]
                             [:and [:= :it/entity_type "company"]
                                   [:= :co/id :it/entity_id]]
                             [:contact :ct]
                             [:and [:= :it/entity_type "contact"]
                                   [:= :ct/id :it/entity_id]]]
                :where     [:and
                            [:is-not :it/reminder_date nil]
                            [:<= :it/reminder_date [:raw "date('now', '+30 days')"]]]
                :order-by  [[:it/reminder_date :asc]]}))
