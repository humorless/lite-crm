(ns lite-crm.logs.handlers
  "HTTP handlers for contact logs."
  (:require [lite-crm.logs.queries :as queries]
            [lite-crm.logs.views :as views]
            [reitit-extras.core :as ext]))

(defn update-handler
  "PATCH /logs/:id — toggle pin. Returns updated logs-list fragment."
  [{:keys [context parameters]
    router :reitit.core/router}]
  (let [id        (get-in parameters [:path :id])
        is-pinned (= "true" (get-in parameters [:form :is-pinned]))
        log       (queries/get-log (:db context) id)]
    (queries/toggle-pin! (:db context) id is-pinned)
    (let [logs (queries/list-logs-by-company (:db context) (:company-id log))]
      (-> {:router router :logs logs}
          (views/logs-list-fragment)
          (ext/render-html)))))

(defn delete-handler
  "DELETE /logs/:id — delete log. Returns updated logs-list fragment."
  [{:keys [context parameters]
    router :reitit.core/router}]
  (let [id         (get-in parameters [:path :id])
        log        (queries/get-log (:db context) id)
        company-id (:company-id log)]
    (queries/delete-log! (:db context) id)
    (let [logs (queries/list-logs-by-company (:db context) company-id)]
      (-> {:router router :logs logs}
          (views/logs-list-fragment)
          (ext/render-html)))))

(defn ledger-handler
  "GET /logs — global filterable log ledger."
  [{:keys [context query-params]
    user   :identity
    router :reitit.core/router}]
  (let [filters {:status       (not-empty (:status query-params))
                 :company-name (not-empty (:company-name query-params))
                 :contact-name (not-empty (:contact-name query-params))
                 :date-from    (not-empty (:date-from query-params))
                 :date-to      (not-empty (:date-to query-params))
                 :pinned-only? (= "true" (:pinned-only query-params))}
        logs    (queries/list-all-logs (:db context) filters)]
    (-> {:user user :router router :logs logs :filters query-params}
        (views/ledger-page)
        (ext/render-html))))

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
