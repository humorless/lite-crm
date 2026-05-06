(ns lite-crm.contacts.queries
  "DB queries for contacts."
  (:require [lite-crm.db :as db]))

(defn list-contacts
  "Return contacts filtered by optional :company-id, :name, :email."
  [db {:keys [company-id email] contact-name :name}]
  (let [wheres (cond-> []
                 company-id   (conj [:= :c/company_id company-id])
                 contact-name (conj [:like :c/name (str "%" contact-name "%")])
                 email        (conj [:like :c/email (str "%" email "%")]))
        query  {:select   [:c/id :c/name :c/department :c/title :c/phone :c/phone_ext
                            :c/mobile :c/email :c/notes :c/created_at
                            [:co/name :company-name] [:co/id :company-id]]
                :from     [[:contact :c]]
                :left-join [[:company :co] [:= :co/id :c/company_id]]
                :order-by [[:c/name :asc]]}
        query  (if (seq wheres) (assoc query :where (into [:and] wheres)) query)]
    (db/exec! db query)))

(defn get-contact
  [db id]
  (db/exec-one! db {:select    [:c/id :c/name :c/company_id :c/department :c/title
                                  :c/phone :c/phone_ext :c/mobile :c/email :c/notes
                                  :c/created_at [:co/name :company-name]]
                    :from      [[:contact :c]]
                    :left-join [[:company :co] [:= :co/id :c/company_id]]
                    :where     [:= :c/id id]}))

(defn create-contact!
  [db {:keys [company-id department title phone phone-ext mobile email notes] contact-name :name}]
  (db/exec-one! db {:insert-into :contact
                    :values      [(cond-> {:name contact-name}
                                    company-id  (assoc :company_id company-id)
                                    department  (assoc :department department)
                                    title       (assoc :title title)
                                    phone       (assoc :phone phone)
                                    phone-ext   (assoc :phone_ext phone-ext)
                                    mobile      (assoc :mobile mobile)
                                    email       (assoc :email email)
                                    notes       (assoc :notes notes))]
                    :returning   [:*]}))

(defn update-contact!
  [db id {:keys [company-id department title phone phone-ext mobile email notes] contact-name :name}]
  (db/exec-one! db {:update    :contact
                    :set       (cond-> {}
                                 (some? contact-name) (assoc :name contact-name)
                                 (some? company-id) (assoc :company_id company-id)
                                 (some? department) (assoc :department department)
                                 (some? title)      (assoc :title title)
                                 (some? phone)      (assoc :phone phone)
                                 (some? phone-ext)  (assoc :phone_ext phone-ext)
                                 (some? mobile)     (assoc :mobile mobile)
                                 (some? email)      (assoc :email email)
                                 (some? notes)      (assoc :notes notes))
                    :where     [:= :id id]
                    :returning [:*]}))

(defn list-contacts-by-company
  [db company-id]
  (db/exec! db {:select   [:id :name :department :title :phone :phone_ext :mobile :email]
                :from     [:contact]
                :where    [:= :company_id company-id]
                :order-by [[:name :asc]]}))

(defn list-logs-for-contact
  "All logs this contact appears in, newest first."
  [db contact-id]
  (db/exec! db {:select   [:cl/id :cl/date :cl/content :cl/status :cl/is_pinned
                            [:co/id :company-id] [:co/name :company-name]]
                :from     [[:log_contact :lc]]
                :join     [[:contact_log :cl] [:= :cl/id :lc/log_id]
                           [:company :co]     [:= :co/id :cl/company_id]]
                :where    [:= :lc/contact_id contact-id]
                :order-by [[:cl/date :desc]]}))
