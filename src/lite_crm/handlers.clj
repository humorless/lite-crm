(ns lite-crm.handlers
  (:require [lite-crm.logs.queries :as log-queries]
            [lite-crm.tags.queries :as tag-queries]
            [lite-crm.views :as views]
            [reitit-extras.core :as reitit-extras]
            [ring.util.response :as response]))

(defn default-handler
  [error-text status-code]
  (fn [_]
    (-> (views/error-page error-text)
        (reitit-extras/render-html)
        (response/status status-code))))

(defn home-handler
  [{:keys [context]
    user   :identity
    router :reitit.core/router}]
  (let [reminders   (tag-queries/list-upcoming-reminders (:db context))
        recent-logs (log-queries/list-recent-logs (:db context))]
    (-> {:user user :router router
         :reminders reminders :recent-logs recent-logs}
        (views/home-page)
        (reitit-extras/render-html))))
