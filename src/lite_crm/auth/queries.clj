(ns lite-crm.auth.queries
  "User persistence queries against XTDB."
  (:require [buddy.hashers :as hashers]
            [lite-crm.db :as db])
  (:import java.sql.SQLException))

(defn get-user
  "Return the user map for email, or nil if not found.
   Returned keys: :id :email :password"
  [db email]
  (db/exec-one! db {:select [[:_id :id] :email :password]
                    :from [:user]
                    :where [:= :email email]}))

(defn create-user!
  "Insert a new user and return {:id email :email email}.
   Throws java.sql.SQLException with 'unique constraint' in message when email is taken."
  [db {:keys [email password]}]
  (when (some? (get-user db email))
    (throw (SQLException. "unique constraint: email already registered")))
  (db/exec-one! db {:insert-into :user
                    :values [{:_id email
                              :email email
                              :password (hashers/derive password {:alg :bcrypt+sha512})}]})
  {:id email :email email})

(defn update-password!
  "Update the hashed password for the user identified by id (= email used as _id)."
  [db {:keys [id password-hash]}]
  (db/exec-one! db {:update :user
                    :set {:password password-hash}
                    :where [:= :_id id]}))
