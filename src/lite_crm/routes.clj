(ns lite-crm.routes
  (:require [buddy.auth :as buddy-auth]
            [buddy.auth.backends :as backends]
            [buddy.auth.middleware :as auth-middleware]
            [lite-crm.auth.handlers :as auth-handlers]
            [lite-crm.auth.spec :as spec]
            [lite-crm.companies.handlers :as company-handlers]
            [lite-crm.contacts.handlers :as contact-handlers]
            [lite-crm.handlers :as handlers]
            [lite-crm.logs.handlers :as log-handlers]
            [lite-crm.tags.handlers :as tag-handlers]
            [reitit-extras.core :as ext]
            [ring.util.response :as response]))

(defn wrap-login-required
  "Middleware used in routes that require authentication. Buddy checks
  if request key :identity is set to truthy value by any previous middleware.
  If the request is not authenticated, then redirect to Login page."
  [handler]
  (fn [{router :reitit.core/router
        :as request}]
    (if (buddy-auth/authenticated? request)
      (handler request)
      (response/redirect (ext/get-route router ::login)))))

(defn wrap-already-logged-in
  "Middleware used in routes that require authentication. Buddy checks
  if request key :identity is set to truthy value by any previous middleware.
  If the request is not authenticated, then redirect to Login page."
  [handler]
  (fn [{router :reitit.core/router
        :as request}]
    (if (buddy-auth/authenticated? request)
      (response/redirect (ext/get-route router ::home))
      (handler request))))

(def routes
  (let [auth-backend (backends/session)]
    [["/"
      {:middleware [[auth-middleware/wrap-authentication auth-backend] wrap-login-required]}
      ["" {:name ::home
           :get  {:handler   handlers/home-handler
                  :responses {200 {:body string?}}}}]]
     ["/health" {:name ::health
                 :get {:handler (fn [_] (response/response "OK"))}}]
     ["/auth"
      [""
       {:middleware [[auth-middleware/wrap-authentication auth-backend]
                     wrap-already-logged-in]}
       ["/register" {:name ::register
                     :get {:handler auth-handlers/get-register}
                     :post {:handler auth-handlers/post-register
                            :parameters {:form [:map
                                                [:email spec/Email]
                                                [:password [:string {:min 8}]]]}
                            :responses {200 {:body string?}}}}]
       ["/login" {:name ::login
                  :get {:handler auth-handlers/get-login}
                  :post {:handler auth-handlers/post-login
                         :parameters {:form [:map
                                             [:email spec/Email]
                                             [:password [:string {:min 1}]]]}
                         :responses {200 {:body string?}}}}]
       ["/forgot-password" {:name ::forgot-password
                            :get {:handler auth-handlers/get-forgot-password}
                            :post {:handler auth-handlers/post-forgot-password
                                   :parameters {:form [:map
                                                       [:email spec/Email]]}
                                   :responses {200 {:body string?}}}}]
       ["/reset-password" {:name ::reset-password
                           :get {:handler auth-handlers/get-reset-password
                                 :parameters {:query [:map
                                                      [:token string?]]}}
                           :post {:handler auth-handlers/post-reset-password
                                  :parameters {:form [:map
                                                      [:password [:string {:min 8}]]
                                                      [:confirm-password [:string {:min 8}]]
                                                      [:token string?]]}
                                  :responses {200 {:body string?}}}}]]
      ["/logout" {:name ::logout
                  :middleware [[auth-middleware/wrap-authentication auth-backend]]
                  :post {:handler auth-handlers/post-logout}}]]
     ["/account"
      {:middleware [[auth-middleware/wrap-authentication auth-backend]
                    wrap-login-required]}
      ["" {:name ::account
           :get {:handler auth-handlers/get-account
                 :responses {200 {:body string?}}}}]
      ["/change-password" {:name ::change-password
                           :post {:handler auth-handlers/post-change-password
                                  :parameters {:form [:map
                                                      [:current-password [:string {:min 1}]]
                                                      [:new-password [:string {:min 8}]]
                                                      [:confirm-new-password [:string {:min 8}]]]}
                                  :responses {200 {:body string?}}}}]]
     ["/companies"
      {:middleware [[auth-middleware/wrap-authentication auth-backend]
                    wrap-login-required]}
      ["" {:name      ::companies
           :get       {:handler   company-handlers/list-handler
                       :responses {200 {:body string?}}}
           :post      {:handler    company-handlers/create-handler
                       :parameters {:form [:map
                                           [:name [:string {:min 1}]]
                                           [:industry {:optional true} string?]
                                           [:tier {:optional true} string?]
                                           [:notes {:optional true} string?]]}
                       :responses  {200 {:body string?}}}}]
      ["/new" {:name        ::new-company
               :conflicting true
               :get         {:handler   company-handlers/new-handler
                             :responses {200 {:body string?}}}}]
      ["/import"
       [""  {:name        ::companies-import
             :conflicting true
             :get         {:handler   company-handlers/import-page-handler
                           :responses {200 {:body string?}}}
             :post        {:handler    company-handlers/import-preview-handler
                           :parameters {:multipart [:map [:csv-file any?]]}
                           :responses  {200 {:body string?}}}}]
       ["/confirm"
        {:name    ::companies-import-confirm
         :post    {:handler    company-handlers/import-confirm-handler
                   :parameters {:form [:map
                                       [:rows-json [:string {:min 1}]]
                                       [:name-col      {:optional true} string?]
                                       [:industry-col  {:optional true} string?]
                                       [:tier-col      {:optional true} string?]]}
                   :responses  {200 {:body string?}}}}]]
      ["/:id"
       {:conflicting true
        :parameters  {:path [:map [:id pos-int?]]}}
       ["" {:name  ::company
            :get   {:handler   company-handlers/detail-handler
                    :responses {200 {:body string?}}}
            :patch {:handler    company-handlers/update-handler
                    :parameters {:form [:map
                                        [:name {:optional true} [:string {:min 1}]]
                                        [:industry {:optional true} string?]
                                        [:tier {:optional true} string?]
                                        [:notes {:optional true} string?]]}
                    :responses  {200 {:body string?}}}}]
       ["/tabs/:tab"
        {:name       ::company-tab
         :parameters {:path [:map [:id pos-int?] [:tab string?]]}
         :get        {:handler   company-handlers/tab-handler
                      :responses {200 {:body string?}}}}]
       ["/addresses"
        {:name       ::company-addresses
         :parameters {:path [:map [:id pos-int?]]}
         :post       {:handler    company-handlers/create-address-handler
                      :parameters {:form [:map
                                          [:address [:string {:min 1}]]
                                          [:label {:optional true} string?]]}
                      :responses  {200 {:body string?}}}}]
       ["/addresses/:addr-id"
        {:name       ::company-address
         :parameters {:path [:map [:id pos-int?] [:addr-id pos-int?]]}
         :delete     {:handler   company-handlers/delete-address-handler
                      :responses {200 {:body string?}}}}]
       ["/phones"
        {:name       ::company-phones
         :parameters {:path [:map [:id pos-int?]]}
         :post       {:handler    company-handlers/create-phone-handler
                      :parameters {:form [:map
                                          [:phone [:string {:min 1}]]
                                          [:label {:optional true} string?]]}
                      :responses  {200 {:body string?}}}}]
       ["/phones/:phone-id"
        {:name       ::company-phone
         :parameters {:path [:map [:id pos-int?] [:phone-id pos-int?]]}
         :delete     {:handler   company-handlers/delete-phone-handler
                      :responses {200 {:body string?}}}}]
       ["/logs"
        {:name       ::company-logs
         :parameters {:path [:map [:id pos-int?]]}
         :post       {:handler    log-handlers/create-handler
                      :parameters {:form [:map
                                          [:date    [:string {:min 1}]]
                                          [:content [:string {:min 1}]]
                                          [:status        {:optional true} string?]
                                          [:contact-ids   {:optional true}
                                           [:or string? [:vector string?]]]]}
                      :responses  {200 {:body string?}}}}]]]
     ["/contacts"
      {:middleware [[auth-middleware/wrap-authentication auth-backend] wrap-login-required]}
      [""  {:name ::contacts
            :get  {:handler   contact-handlers/list-handler
                   :responses {200 {:body string?}}}
            :post {:handler    contact-handlers/create-handler
                   :parameters {:form [:map
                                       [:name       [:string {:min 1}]]
                                       [:company-id {:optional true} string?]
                                       [:department {:optional true} string?]
                                       [:title      {:optional true} string?]
                                       [:phone      {:optional true} string?]
                                       [:phone-ext  {:optional true} string?]
                                       [:mobile     {:optional true} string?]
                                       [:email      {:optional true} string?]
                                       [:notes      {:optional true} string?]]}
                   :responses  {302 {:body string?}}}}]
      ["/new" {:name        ::contacts-new
               :conflicting true
               :get         {:handler   contact-handlers/new-handler
                             :responses {200 {:body string?}}}}]
      ["/:id"
       {:conflicting true
        :parameters  {:path [:map [:id pos-int?]]}}
       ["" {:name  ::contact
            :get   {:handler   contact-handlers/detail-handler
                    :responses {200 {:body string?}}}
            :patch {:handler    contact-handlers/update-handler
                    :parameters {:form [:map
                                        [:name       {:optional true} [:string {:min 1}]]
                                        [:company-id {:optional true} string?]
                                        [:department {:optional true} string?]
                                        [:title      {:optional true} string?]
                                        [:phone      {:optional true} string?]
                                        [:phone-ext  {:optional true} string?]
                                        [:mobile     {:optional true} string?]
                                        [:email      {:optional true} string?]
                                        [:notes      {:optional true} string?]]}
                    :responses  {200 {:body string?}}}}]]]
     ["/tags"
      {:middleware [[auth-middleware/wrap-authentication auth-backend] wrap-login-required]}
      [""  {:name ::tags
            :post {:handler    tag-handlers/create-handler
                   :parameters {:form [:map
                                       [:entity-type [:enum "company" "contact"]]
                                       [:entity-id   pos-int?]
                                       [:name        [:string {:min 1}]]
                                       [:reminder-date {:optional true} string?]
                                       [:notes         {:optional true} string?]]}
                   :responses  {200 {:body string?}}}}]
      ["/:id"
       {:name       ::tag
        :parameters {:path [:map [:id pos-int?]]}
        :delete     {:handler    tag-handlers/delete-handler
                     :parameters {:form [:map
                                         [:entity-type [:enum "company" "contact"]]
                                         [:entity-id   pos-int?]]}
                     :responses  {200 {:body string?}}}}]]
     ["/logs"
      {:middleware [[auth-middleware/wrap-authentication auth-backend]
                    wrap-login-required]}
      ["" {:name ::logs
           :get  {:handler   log-handlers/ledger-handler
                  :responses {200 {:body string?}}}}]
      ["/:id"
       {:name       ::log
        :parameters {:path [:map [:id pos-int?]]}
        :patch      {:handler    log-handlers/update-handler
                     :parameters {:form [:map [:is-pinned {:optional true} string?]]}
                     :responses  {200 {:body string?}}}
        :delete     {:handler   log-handlers/delete-handler
                     :responses {200 {:body string?}}}}]]]))
