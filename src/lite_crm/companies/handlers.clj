(ns lite-crm.companies.handlers
  "HTTP handlers for companies list and create."
  (:require [lite-crm.companies.queries :as queries]
            [lite-crm.companies.views :as views]
            [lite-crm.routes :as-alias routes]
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
