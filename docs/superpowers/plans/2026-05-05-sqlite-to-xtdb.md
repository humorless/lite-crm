# SQLite → XTDB Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace SQLite + Ragtime with XTDB 2.x as the persistence layer, keeping HikariCP / next.jdbc / HoneySQL.

**Architecture:** XTDB 2.1.0 exposes a Postgres wire protocol on port 5432. HikariCP connects via the standard Postgres JDBC driver; next.jdbc and HoneySQL work unchanged against it. XTDB is schemaless so migrations are deleted. Each user record stores `email` as the XTDB `_id` field, giving free uniqueness enforcement without DDL.

**Tech Stack:** XTDB 2.1.0 (Docker), `org.postgresql/postgresql 42.7.4`, HikariCP 3.3.0, next.jdbc 1.3.1048, HoneySQL 2.7.1340.

---

## Prerequisites

- XTDB Docker container must be running before starting any REPL session or test run:
  ```bash
  cd xtdb && docker compose up -d
  ```
- Verify it is healthy:
  ```bash
  curl http://localhost:3000/healthz
  # expected: {"status":"ok"}
  ```

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `deps.edn` | Modify | Remove `sqlite-jdbc`, `ragtime`; add `org.postgresql/postgresql` |
| `resources/config.edn` | Modify | JDBC URL → XTDB Postgres endpoint (both default and test profiles) |
| `src/lite_crm/db.clj` | Modify | Remove Ragtime migration call and unused imports |
| `src/lite_crm/auth/queries.clj` | Rewrite | Three user-query functions using XTDB-compatible SQL |
| `test/lite_crm/test_utils.clj` | Modify | Replace `sqlite_master` table discovery with direct `DELETE FROM user` |
| `resources/migrations/0001.up.sql` | Delete | No longer needed |
| `resources/migrations/0002.up.sql` | Delete | No longer needed |

Files **not changed:** `server.clj`, `routes.clj`, `handlers.clj`, `views.clj`, `auth/handlers.clj`, `auth/views.clj`, `auth/spec.clj`, `bb.edn`.

---

## Task 1: Update deps.edn and config.edn

**Files:**
- Modify: `deps.edn`
- Modify: `resources/config.edn`

- [ ] **Step 1: Remove SQLite and Ragtime deps, add Postgres JDBC driver**

  Replace the `;; db` section in `deps.edn`:

  ```clojure
  ; db
  hikari-cp/hikari-cp {:mvn/version "3.3.0"}
  org.postgresql/postgresql {:mvn/version "42.7.4"}
  com.github.seancorfield/next.jdbc {:mvn/version "1.3.1048"}
  com.github.seancorfield/honeysql {:mvn/version "2.7.1340"}
  ```

  (Delete the `org.xerial/sqlite-jdbc` and `dev.weavejester/ragtime` lines entirely.)

- [ ] **Step 2: Update JDBC URLs in config.edn**

  Replace the entire `config.edn`:

  ```edn
  {:lite-crm.db/db
   {:jdbc-url "jdbc:postgresql://localhost:5432/xtdb"}

   :lite-crm.server/server
   {:options {:port #profile {:default 8000
                              :prod 80
                              :test #free-port true}
              :session-secret-key #profile {:default "test-secret-key"
                                            :prod #env SESSION_SECRET_KEY}
              :auto-reload? #profile {:default false
                                      :dev true}
              :cache-assets? #profile {:default false
                                       :prod true}}
    :db #ig/ref :lite-crm.db/db}}
  ```

  Note: both dev and test now point to the same running XTDB instance. The `#profile` selector for `:jdbc-url` is removed entirely.

- [ ] **Step 3: Verify deps resolve**

  ```bash
  clj -P
  ```

  Expected: exits cleanly with no `Could not find artifact` errors.

---

## Task 2: Simplify db.clj — remove Ragtime

**Files:**
- Modify: `src/lite_crm/db.clj`

- [ ] **Step 1: Rewrite db.clj**

  ```clojure
  (ns lite-crm.db
    "Manages the XTDB database connection pool via Postgres wire protocol."
    (:require [clojure.tools.logging :as log]
              [hikari-cp.core :as cp]
              [honey.sql :as honey]
              [integrant-extras.core :as ig-extras]
              [integrant.core :as ig]
              [next.jdbc :as jdbc]
              [next.jdbc.result-set :as jdbc-rs]))

  (def ^:private sql-params
    {:builder-fn jdbc-rs/as-unqualified-kebab-maps})

  (defn exec!
    "Send query to db and return vector of result items."
    [db query]
    (let [query-sql (honey/format query {:quoted true})]
      (jdbc/execute! db query-sql sql-params)))

  (defn exec-one!
    "Send query to db and return single result item."
    [db query]
    (let [query-sql (honey/format query {:quoted true})]
      (jdbc/execute-one! db query-sql sql-params)))

  (defmethod ig/assert-key ::db
    [_ params]
    (ig-extras/validate-schema!
      {:component ::db
       :data params
       :schema [:map
                [:jdbc-url string?]]}))

  (defmethod ig/init-key ::db
    [_ options]
    (log/info "[DB] Starting database connection pool...")
    (cp/make-datasource options))

  (defmethod ig/halt-key! ::db
    [_ datasource]
    (log/info "[DB] Closing database connection pool...")
    (cp/close-datasource datasource))
  ```

- [ ] **Step 2: Lint**

  ```bash
  clj-kondo --lint src/lite_crm/db.clj
  ```

  Expected: no warnings.

- [ ] **Step 3: Start REPL and verify system boots**

  ```bash
  bb clj-repl
  ```

  In the REPL:

  ```clojure
  (reset)
  ```

  Expected: log lines `[DB] Starting database connection pool...` and `[SERVER] Starting server...` with no exceptions. If HikariCP complains about missing credentials, add `:username "xtdb" :password "xtdb"` to the `:lite-crm.db/db` map in `config.edn` and retry.

---

## Task 3: Rewrite auth/queries.clj for XTDB

**Files:**
- Rewrite: `src/lite_crm/auth/queries.clj`

Context on XTDB SQL:
- Every XTDB row must have a `_id` column (the document key). We use `email` as `_id`, giving free uniqueness enforcement.
- HoneySQL with `:quoted true` (set in `db/exec-one!`) quotes identifiers with double quotes. `"_id"` is valid in XTDB.
- `INSERT` does not support `RETURNING` in XTDB 2.x, so `create-user!` returns a hand-built map.
- `SELECT _id AS id` aliases `_id` so `next.jdbc`'s `as-unqualified-kebab-maps` produces `:id` (not the awkward `:-id` that `_id` would produce).

- [ ] **Step 1: Rewrite queries.clj**

  ```clojure
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
  ```

- [ ] **Step 2: Lint**

  ```bash
  clj-kondo --lint src/lite_crm/auth/queries.clj
  ```

  Expected: no warnings.

- [ ] **Step 3: Smoke-test queries in REPL**

  Requires REPL from Task 2. If system is already running with `(reset)`:

  ```clojure
  (require '[integrant.repl.state :as state]
           '[lite-crm.auth.queries :as q])
  (def test-db (:lite-crm.db/db state/system))

  ; create a test user
  (q/create-user! test-db {:email "smoke@test.com" :password "hunter2hunter2"})
  ; => {:id "smoke@test.com" :email "smoke@test.com"}

  ; fetch it back
  (q/get-user test-db "smoke@test.com")
  ; => {:id "smoke@test.com" :email "smoke@test.com" :password "<bcrypt-hash>"}

  ; duplicate should throw
  (try
    (q/create-user! test-db {:email "smoke@test.com" :password "other"})
    :no-exception
    (catch java.sql.SQLException e
      (ex-message e)))
  ; => "unique constraint: email already registered"

  ; update password
  (require '[buddy.hashers :as hashers])
  (q/update-password! test-db {:id "smoke@test.com"
                                :password-hash (hashers/derive "newpass1234" {:alg :bcrypt+sha512})})
  ; => {:next.jdbc/update-count 1}  (or nil — both are fine)

  ; verify password changed
  (let [u (q/get-user test-db "smoke@test.com")]
    (hashers/verify "newpass1234" (:password u) {:alg :bcrypt+sha512}))
  ; => {:valid true :update false}
  ```

  Clean up smoke user after testing:

  ```clojure
  (require '[lite-crm.db :as db])
  (db/exec! test-db {:delete-from :user :where [:= :_id "smoke@test.com"]})
  ```

---

## Task 4: Update test_utils.clj

**Files:**
- Modify: `test/lite_crm/test_utils.clj`

- [ ] **Step 1: Replace SQLite-specific table discovery with direct DELETE**

  ```clojure
  (ns lite-crm.test-utils
    "Test helpers: fixture for table cleanup and response parsing."
    (:require [hickory.core :as hickory]
              [integrant-extras.tests :as ig-extras]
              [lite-crm.db :as db]
              [lite-crm.server :as server]))

  (def ^:const TEST-CSRF-TOKEN "test-csrf-token")
  (def ^:const TEST-SECRET-KEY "test-secret-key")

  (defn with-truncated-tables
    "Delete all rows from user-managed tables between tests."
    [f]
    (let [db (::db/db ig-extras/*test-system*)]
      (db/exec! db {:delete-from :user})
      (f)))

  (defn response->hickory
    "Convert a Ring response body to a Hickory document."
    [response]
    (-> response
        :body
        (hickory/parse)
        (hickory/as-hickory)))

  (defn db
    "Get the database connection from the test system."
    []
    (::db/db ig-extras/*test-system*))

  (defn server
    "Get the server instance from the test system."
    []
    (::server/server ig-extras/*test-system*))
  ```

- [ ] **Step 2: Lint**

  ```bash
  clj-kondo --lint test/lite_crm/test_utils.clj
  ```

  Expected: no warnings.

---

## Task 5: Delete migration files

**Files:**
- Delete: `resources/migrations/0001.up.sql`
- Delete: `resources/migrations/0002.up.sql`

- [ ] **Step 1: Delete migration files**

  ```bash
  rm resources/migrations/0001.up.sql resources/migrations/0002.up.sql
  ```

- [ ] **Step 2: Verify directory is now empty**

  ```bash
  ls resources/migrations/
  ```

  Expected: empty output.

---

## Task 6: Run full test suite

**Prerequisites:** XTDB Docker container running (`cd xtdb && docker compose up -d`).

- [ ] **Step 1: Run tests**

  ```bash
  bb test
  ```

  Expected: all tests pass. XTDB is the live backing store; `with-truncated-tables` deletes all user rows between each test.

- [ ] **Step 2: If any test fails — common issues**

  | Symptom | Cause | Fix |
  |---|---|---|
  | `Connection refused` on port 5432 | XTDB not running | `cd xtdb && docker compose up -d` |
  | `HikariPool-1 - Exception during pool initialization` | Credentials rejected | Add `:username "xtdb" :password "xtdb"` to the `:lite-crm.db/db` map in `config.edn` |
  | `relation "user" does not exist` | Query ran before first INSERT | XTDB creates tables implicitly on first INSERT; check test ordering |
  | Test for duplicate email fails | `get-user` returning wrong result | Check `_id` alias in SELECT: `[[:_id :id] :email :password]` |

- [ ] **Step 3: Commit**

  ```bash
  git add deps.edn resources/config.edn src/lite_crm/db.clj \
          src/lite_crm/auth/queries.clj test/lite_crm/test_utils.clj \
          docs/superpowers/plans/2026-05-05-sqlite-to-xtdb.md
  git rm resources/migrations/0001.up.sql resources/migrations/0002.up.sql
  git commit -m "feat: migrate persistence layer from SQLite to XTDB 2.x

  - Remove sqlite-jdbc, ragtime; add org.postgresql/postgresql
  - XTDB connected via Postgres wire protocol through HikariCP
  - auth/queries rewritten: email used as XTDB _id for uniqueness
  - No migrations needed (XTDB is schemaless)
  - Tests require running XTDB Docker instance (see xtdb/docker-compose.yml)"
  ```
