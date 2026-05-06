(ns lite-crm.logs.handlers
  "HTTP handlers for contact logs."
  (:require [lite-crm.logs.queries :as queries]
            [lite-crm.logs.views :as views]
            [reitit-extras.core :as ext]))

(defn create-handler
  "POST /companies/:id/logs"
  [{:keys [context parameters]
    user   :identity
    router :reitit.core/router}]
  (let [company-id   (get-in parameters [:path :id])
        {:keys [date content status contact-ids]} (:form parameters)
        contact-ids-vec (cond
                          (nil? contact-ids)    []
                          (string? contact-ids) [(parse-long contact-ids)]
                          :else                 (mapv parse-long contact-ids))]
    (queries/create-log! (:db context)
                         {:company-id company-id
                          :date       date
                          :content    content
                          :status     status
                          :created-by (:id user)}
                         contact-ids-vec)
    (let [logs (queries/list-logs-by-company (:db context) company-id)]
      (-> {:router router :logs logs}
          (views/logs-list-fragment)
          (ext/render-html)))))
