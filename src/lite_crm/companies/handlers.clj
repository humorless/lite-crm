(ns lite-crm.companies.handlers
  "HTTP handlers for companies list, create, and detail."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [jsonista.core :as json]
            [lite-crm.companies.import :as csv-import]
            [lite-crm.companies.queries :as queries]
            [lite-crm.companies.views :as views]
            [lite-crm.contacts.queries :as contact-queries]
            [lite-crm.contacts.views :as contact-views]
            [lite-crm.logs.queries :as log-queries]
            [lite-crm.logs.views :as log-views]
            [lite-crm.routes :as-alias routes]
            [lite-crm.tags.handlers :as tag-handlers]
            [reitit-extras.core :as ext]
            [ring.util.response :as response]))

(defn list-handler
  [{:keys [context query-params]
    user   :identity
    router :reitit.core/router}]
  (let [filters   {:tier         (:tier query-params)
                   :tag-name     (:tag-name query-params)
                   :company-name (:company-name query-params)}
        companies (queries/list-companies (:db context) filters)]
    (-> {:user user :router router :companies companies :filters filters}
        (views/companies-page)
        (ext/render-html))))

(defn new-handler
  [{user   :identity
    router :reitit.core/router}]
  (-> {:user user :router router}
      (views/new-company-page)
      (ext/render-html)))

(defn create-handler
  [{:keys [context errors parameters params]
    user   :identity
    router :reitit.core/router}]
  (if (some? errors)
    (-> {:user user :router router :values params :errors (:humanized errors)}
        (views/new-company-form)
        (ext/render-html))
    (let [{:keys [industry tier notes] company-name :name} (:form parameters)]
      (queries/create-company! (:db context) {:name     company-name
                                               :industry industry
                                               :tier     tier
                                               :notes    notes})
      (-> (ext/render-html [:div])
          (response/header "HX-Redirect" (ext/get-route router ::routes/companies))))))

(defn detail-handler
  [{:keys [context parameters]
    user   :identity
    router :reitit.core/router}]
  (let [id      (get-in parameters [:path :id])
        company (queries/get-company (:db context) id)]
    (if (nil? company)
      (response/not-found "Company not found")
      (let [addresses (queries/list-addresses (:db context) id)
            phones    (queries/list-phones (:db context) id)]
        (-> {:user user :router router :company company
             :addresses addresses :phones phones}
            (views/company-page)
            (ext/render-html))))))

(defn tab-handler
  "Returns tab content fragment for HTMX tab switching."
  [{:keys [context parameters query-params]
    router :reitit.core/router}]
  (let [id      (get-in parameters [:path :id])
        tab     (get-in parameters [:path :tab])
        company (queries/get-company (:db context) id)]
    (case tab
      "info"
      (let [addresses    (queries/list-addresses (:db context) id)
            phones       (queries/list-phones (:db context) id)
            editing?     (= "true" (:editing query-params))
            tags-section (tag-handlers/tags-section-fragment (:db context) "company" id)]
        (-> {:router router :company company
             :addresses addresses :phones phones :editing? editing?
             :tags-section tags-section}
            (views/info-tab-content)
            (ext/render-html)))
      "contacts"
      (let [contacts (contact-queries/list-contacts-by-company (:db context) id)
            company  (queries/get-company (:db context) id)]
        (-> {:router router :company company :contacts contacts}
            (contact-views/company-contacts-tab)
            (ext/render-html)))
      "logs"
      (let [logs    (log-queries/list-logs-by-company (:db context) id)
            company (queries/get-company (:db context) id)]
        (-> {:router router :company company :logs logs :contacts []}
            (log-views/logs-tab-content)
            (ext/render-html)))
      (ext/render-html [:div {:class ["py-8" "text-center" "text-gray-400"]} "即將推出"]))))

(defn create-address-handler
  [{:keys [context parameters]
    router :reitit.core/router}]
  (let [id   (get-in parameters [:path :id])
        {:keys [label address]} (:form parameters)]
    (queries/create-address! (:db context) {:company-id id :label label :address address})
    (let [addresses (queries/list-addresses (:db context) id)
          company   (queries/get-company (:db context) id)]
      (-> {:router router :company company :addresses addresses}
          (views/addresses-section)
          (ext/render-html)))))

(defn delete-address-handler
  [{:keys [context parameters]
    router :reitit.core/router}]
  (let [id      (get-in parameters [:path :id])
        addr-id (get-in parameters [:path :addr-id])]
    (queries/delete-address! (:db context) addr-id)
    (let [addresses (queries/list-addresses (:db context) id)
          company   (queries/get-company (:db context) id)]
      (-> {:router router :company company :addresses addresses}
          (views/addresses-section)
          (ext/render-html)))))

(defn create-phone-handler
  [{:keys [context parameters]
    router :reitit.core/router}]
  (let [id  (get-in parameters [:path :id])
        {:keys [label phone]} (:form parameters)]
    (queries/create-phone! (:db context) {:company-id id :label label :phone phone})
    (let [phones  (queries/list-phones (:db context) id)
          company (queries/get-company (:db context) id)]
      (-> {:router router :company company :phones phones}
          (views/phones-section)
          (ext/render-html)))))

(defn delete-phone-handler
  [{:keys [context parameters]
    router :reitit.core/router}]
  (let [id       (get-in parameters [:path :id])
        phone-id (get-in parameters [:path :phone-id])]
    (queries/delete-phone! (:db context) phone-id)
    (let [phones  (queries/list-phones (:db context) id)
          company (queries/get-company (:db context) id)]
      (-> {:router router :company company :phones phones}
          (views/phones-section)
          (ext/render-html)))))

(defn import-page-handler
  [{user   :identity
    router :reitit.core/router}]
  (-> {:user user :router router}
      (views/import-page)
      (ext/render-html)))

(defn import-preview-handler
  "POST /companies/import — parse CSV multipart upload, return preview fragment."
  [{:keys [parameters multipart-params]
    router :reitit.core/router}]
  (let [file-part    (or (get-in parameters [:multipart :csv-file])
                         (get multipart-params "csv-file"))
        input-stream (cond
                       (string? file-part)   (io/input-stream (.getBytes ^String file-part "UTF-8"))
                       (:stream file-part)   (:stream file-part)
                       (:tempfile file-part) (io/input-stream (:tempfile file-part)))
        {:keys [headers rows]} (csv-import/parse-csv-stream input-stream)
        preview-rows (take 5 rows)
        all-maps     (->> rows
                          (keep (fn [row]
                                  (let [name-v (str/trim (first row))]
                                    (when (seq name-v)
                                      {"name"     name-v
                                       "industry" (not-empty (str/trim (nth row 1 "")))
                                       "tier"     (not-empty (str/trim (nth row 2 "")))}))))
                          (vec))
        rows-json    (json/write-value-as-string all-maps)]
    (-> {:router router :headers headers :preview-rows preview-rows :rows-json rows-json}
        (views/import-preview-fragment)
        (ext/render-html))))

(defn import-confirm-handler
  "POST /companies/import/confirm — decode rows JSON and insert companies."
  [{:keys [context parameters]}]
  (let [rows-json  (get-in parameters [:form :rows-json])
        raw-maps   (csv-import/rows-json->company-maps rows-json)
        result     (csv-import/do-import! (:db context) raw-maps)]
    (-> result
        (views/import-result-fragment)
        (ext/render-html))))

(defn update-handler
  "PATCH /companies/:id — updates info fields, returns info tab content."
  [{:keys [context errors parameters]
    router :reitit.core/router}]
  (let [id (get-in parameters [:path :id])]
    (if (some? errors)
      (let [company      (queries/get-company (:db context) id)
            addresses    (queries/list-addresses (:db context) id)
            phones       (queries/list-phones (:db context) id)
            tags-section (tag-handlers/tags-section-fragment (:db context) "company" id)]
        (-> {:router router :company (merge company (:form parameters))
             :addresses addresses :phones phones :editing? true :errors (:humanized errors)
             :tags-section tags-section}
            (views/info-tab-content)
            (ext/render-html)))
      (let [{:keys [industry tier notes] company-name :name} (:form parameters)]
        (queries/update-company! (:db context) id {:name     company-name
                                                    :industry industry
                                                    :tier     tier
                                                    :notes    notes})
        (let [company      (queries/get-company (:db context) id)
              addresses    (queries/list-addresses (:db context) id)
              phones       (queries/list-phones (:db context) id)
              tags-section (tag-handlers/tags-section-fragment (:db context) "company" id)]
          (-> {:router router :company company
               :addresses addresses :phones phones :editing? false
               :tags-section tags-section}
              (views/info-tab-content)
              (ext/render-html)))))))
