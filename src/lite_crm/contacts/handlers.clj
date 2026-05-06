(ns lite-crm.contacts.handlers
  "HTTP handlers for contacts list, create, and detail."
  (:require [lite-crm.companies.queries :as company-queries]
            [lite-crm.contacts.queries :as queries]
            [lite-crm.contacts.views :as views]
            [reitit-extras.core :as ext]
            [ring.util.response :as response]))

(defn list-handler
  [{:keys [context query-params]
    user   :identity
    router :reitit.core/router}]
  (let [contacts (queries/list-contacts (:db context)
                                        {:name  (:name query-params)
                                         :email (:email query-params)})]
    (-> {:user user :router router :contacts contacts}
        (views/contacts-page)
        (ext/render-html))))

(defn new-handler
  [{:keys [context query-params]
    user   :identity
    router :reitit.core/router}]
  (let [companies (company-queries/list-companies (:db context) {})]
    (-> {:user user :router router :companies companies
         :params {:company-id (:company-id query-params)}}
        (views/new-contact-page)
        (ext/render-html))))

(defn create-handler
  [{:keys [context parameters errors]
    user   :identity
    router :reitit.core/router}]
  (if (some? errors)
    (let [companies (company-queries/list-companies (:db context) {})]
      (-> {:user user :router router :companies companies
           :params (:form parameters) :errors (:humanized errors)}
          (views/new-contact-page)
          (ext/render-html)))
    (let [{:keys [company-id department title phone phone-ext mobile email notes]
           contact-name :name} (:form parameters)
          company-id-long (when (seq company-id) (parse-long company-id))
          contact (queries/create-contact! (:db context)
                                           {:name       contact-name
                                            :company-id company-id-long
                                            :department department
                                            :title      title
                                            :phone      phone
                                            :phone-ext  phone-ext
                                            :mobile     mobile
                                            :email      email
                                            :notes      notes})]
      (response/redirect (str "/contacts/" (:id contact))))))

(defn detail-handler
  [{:keys [context parameters query-params]
    user   :identity
    router :reitit.core/router}]
  (let [id           (get-in parameters [:path :id])
        contact      (queries/get-contact (:db context) id)
        companies    (company-queries/list-companies (:db context) {})
        contact-logs (queries/list-logs-for-contact (:db context) id)
        editing?     (= "true" (:editing query-params))]
    (if (nil? contact)
      (response/not-found "Contact not found")
      (-> {:user user :router router :contact contact
           :companies companies :contact-logs contact-logs :editing? editing?}
          (views/contact-page)
          (ext/render-html)))))

(defn update-handler
  [{:keys [context parameters errors]
    user   :identity
    router :reitit.core/router}]
  (let [id        (get-in parameters [:path :id])
        form      (:form parameters)
        companies (company-queries/list-companies (:db context) {})]
    (if (some? errors)
      (let [contact (queries/get-contact (:db context) id)]
        (-> {:user user :router router :contact (merge contact form)
             :companies companies :editing? true :errors (:humanized errors)}
            (views/contact-info-edit-form)
            (ext/render-html)))
      (let [{:keys [company-id department title phone phone-ext mobile email notes]
             contact-name :name} form
            company-id-long (when (seq (str company-id)) (parse-long (str company-id)))]
        (queries/update-contact! (:db context) id
                                 {:name       contact-name
                                  :company-id company-id-long
                                  :department department
                                  :title      title
                                  :phone      phone
                                  :phone-ext  phone-ext
                                  :mobile     mobile
                                  :email      email
                                  :notes      notes})
        (let [contact (queries/get-contact (:db context) id)]
          (-> {:router router :contact contact}
              (views/contact-info-display)
              (ext/render-html)))))))
