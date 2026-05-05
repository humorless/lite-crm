# Lite CRM — Design Spec
Date: 2026-05-05

## Overview

A lightweight CRM built on top of the existing Clojure Stack Lite project (SQLite + HoneySQL, Reitit + Ring, Hiccup + HTMX + Alpine.js + TailwindCSS). User authentication is already implemented. All CRM data is shared across all logged-in users.

Core needs:
1. Reduce big-table scanning inefficiency
2. Communication ledger must be recorded but sortable by importance (pinning)
3. Visual reminders on dashboard to reduce missed follow-ups

---

## Database Schema

### Tables

```sql
-- Core company record
CREATE TABLE company (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  name        TEXT NOT NULL,
  industry    TEXT,
  tier        TEXT DEFAULT 'no_plan',  -- 'has_plan'|'has_need'|'no_plan'|'abandoned'
  notes       TEXT,
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Multiple addresses per company (all optional)
CREATE TABLE company_address (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  company_id  INTEGER NOT NULL REFERENCES company(id) ON DELETE CASCADE,
  label       TEXT,          -- e.g. "總部", "分公司"
  address     TEXT NOT NULL,
  is_primary  BOOLEAN DEFAULT FALSE
);

-- Multiple phones per company (all optional — company may have no phone yet)
CREATE TABLE company_phone (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  company_id  INTEGER NOT NULL REFERENCES company(id) ON DELETE CASCADE,
  label       TEXT,
  phone       TEXT NOT NULL,
  is_primary  BOOLEAN DEFAULT FALSE
);

-- Contact persons (can change companies — company_id nullable)
CREATE TABLE contact (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  company_id  INTEGER REFERENCES company(id) ON DELETE SET NULL,
  name        TEXT NOT NULL,
  department  TEXT,
  title       TEXT,
  phone       TEXT,
  phone_ext   TEXT,
  mobile      TEXT,
  email       TEXT,
  notes       TEXT,
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Communication ledger; each log belongs to a company
CREATE TABLE contact_log (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  company_id  INTEGER NOT NULL REFERENCES company(id) ON DELETE CASCADE,
  date        DATE NOT NULL,
  content     TEXT NOT NULL,
  status      TEXT,      -- see Log Status Values below
  is_pinned   BOOLEAN DEFAULT FALSE,
  created_by  INTEGER REFERENCES "user"(id),
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Contacts involved in a log (usually 1, sometimes more)
CREATE TABLE log_contact (
  log_id      INTEGER NOT NULL REFERENCES contact_log(id) ON DELETE CASCADE,
  contact_id  INTEGER NOT NULL REFERENCES contact(id) ON DELETE CASCADE,
  PRIMARY KEY (log_id, contact_id)
);

-- Interest tags with optional date reminders; covers both companies and contacts
CREATE TABLE interest_tag (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  entity_type   TEXT NOT NULL,   -- 'company' | 'contact'
  entity_id     INTEGER NOT NULL,
  name          TEXT NOT NULL,   -- e.g. "MDS", "產品X"
  reminder_date DATE,
  notes         TEXT,
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Enum-like Values

**Company tier** (`company.tier`):
| Value | Display |
|---|---|
| `has_plan` | 有規劃 |
| `has_need` | 有需求 |
| `no_plan` | 沒規劃 |
| `abandoned` | 放棄 |

**Log status** (`contact_log.status`):
| Value | Display |
|---|---|
| `no_answer` | 未接 |
| `answered_no_talk` | 接通沒談 |
| `sent_intro` | 寄送自介信 |
| `appointment_set` | 已約訪 |
| `visited` | 已拜訪 |
| `closed` | 成交 |
| `other` | 其他 |

### Key Design Decisions

- `contact.company_id` is nullable: when a contact changes jobs, update `company_id` to the new company. Log history remains on the original company; the contact entity persists as a person.
- `contact_log.company_id` is NOT NULL: every log anchors to a company. A log with no specific contact (e.g., "called front desk") is valid via the `log_contact` join having zero rows.
- Tier and log status are TEXT columns on their respective tables — no separate lookup tables.
- Interest tags are polymorphic via `entity_type` + `entity_id` — one table covers both company and contact tags.

---

## Pages & Routes

All routes under `wrap-login-required` middleware.

| Method | Route | Description |
|---|---|---|
| GET | `/` | Dashboard |
| GET | `/companies` | Companies list |
| GET | `/companies/new` | New company form |
| POST | `/companies` | Create company |
| GET | `/companies/import` | CSV import page |
| POST | `/companies/import` | Process import |
| GET | `/companies/:id` | Company detail (tabbed) |
| PATCH | `/companies/:id` | Update company field |
| GET | `/contacts` | Contacts list |
| GET | `/contacts/new` | New contact form |
| POST | `/contacts` | Create contact |
| GET | `/contacts/:id` | Contact detail |
| PATCH | `/contacts/:id` | Update contact field |
| GET | `/contacts/:id/vcard` | Download vCard (.vcf) |
| GET | `/logs` | Global logs ledger |
| POST | `/logs` | Create log |
| PATCH | `/logs/:id` | Update log (pin/unpin, edit) |
| DELETE | `/logs/:id` | Delete log |
| POST | `/tags` | Add interest tag |
| DELETE | `/tags/:id` | Remove interest tag |

---

## Page Designs

### Dashboard (`/`)

Two panels:

**Upcoming Reminders** (left/top panel)
- Query: `interest_tag WHERE reminder_date <= today + 30 days OR reminder_date < today`, ordered by `reminder_date ASC`
- Each row: entity name (linked to company or contact), tag name, reminder date, notes
- Overdue (past today): red badge; due within 30 days: yellow badge

**Recent Logs** (right/bottom panel)
- Last 20 logs across all companies, ordered by `date DESC`
- Each row: company name (linked), contact name(s), status badge, date, content snippet (truncated)

---

### Companies List (`/companies`)

Filterable table. Filters: tier (dropdown), interest tag name (text search), company name (text search).

Columns: Name | Industry | Tier badge | Last log date | Actions

---

### Company Detail (`/companies/:id`)

Three tabs (HTMX tab swap):

**Info tab**
- Inline-editable fields: name, industry, tier (dropdown), notes
- Addresses section: list of address rows, add/remove
- Phones section: list of phone rows, add/remove
- Interest tags section: list of tags with reminder dates; add new tag form

**Contacts tab**
- List of contacts currently at this company
- Columns: Name | Title | Department | Mobile | Email | Actions
- "Add contact" button → new contact form pre-filled with company

**Logs tab**
- Pinned logs shown first (with pin icon), then remaining logs sorted by `date DESC`
- Each log: date, status badge, contact name(s), content, pin/unpin toggle, edit, delete
- "Add log" button → inline form: date, contact(s) multi-select, status dropdown, content textarea

---

### Contacts List (`/contacts`)

Searchable table. Search by name, company, email.

Columns: Name | Company (linked) | Title | Mobile | Email | vCard | Actions

---

### Contact Detail (`/contacts/:id`)

Fields: name, company (linked, editable), department, title, phone+ext, mobile, email, notes.
Interest tags section (same pattern as company).
Below: all logs this contact appears in (read-only list, links back to company).
vCard download button.

---

### Logs Ledger (`/logs`)

Global view. Filters: status, company (search), contact (search), date range, pinned-only toggle.

Columns: Date | Company | Contact(s) | Status badge | Content (truncated) | Pinned | Actions

---

### CSV Import (`/companies/import`)

1. File upload input (`.csv` or `.xlsx`)
2. On upload: server parses file, returns preview of first 5 rows + column-mapping dropdowns (map CSV columns to: name, industry, tier)
3. Tier mapping: the import page lists expected string values that map to each tier code
4. On confirm: insert rows, skip duplicates by exact name match, return summary (N inserted, M skipped)

---

## Feature Details

### HTMX Interaction Patterns

- **Inline field editing**: click a field → replace with `<input>`, save triggers `hx-patch` → server returns updated field fragment only
- **Tab switching**: `hx-get` on tab button swaps the content panel (`hx-target`, `hx-swap="innerHTML"`)
- **Log pin toggle**: `hx-patch` to `/logs/:id` with `{:is-pinned true/false}`, re-renders the single log row
- **Add log (inline)**: "Add log" button reveals a form inline via `hx-get`; on submit `hx-post` prepends the new log to the list
- **Tag add/remove**: `hx-post` / `hx-delete`, re-renders the tags section

### vCard Export

`GET /contacts/:id/vcard` returns a `.vcf` file with:
- `FN` (full name)
- `ORG` (company name)
- `TITLE` (job title)
- `DEPT` (department)
- `TEL;TYPE=WORK` (phone + ext)
- `TEL;TYPE=CELL` (mobile)
- `EMAIL` (email)

No bulk export in v1.

### Reminder Logic

No background jobs. Dashboard reminder panel is a plain SQL query on every page load:

```sql
SELECT * FROM interest_tag
WHERE reminder_date IS NOT NULL
  AND reminder_date <= date('now', '+30 days')
ORDER BY reminder_date ASC
```

Overdue = `reminder_date < date('now')` → red badge.
Due within 30 days = otherwise → yellow badge.

---

## Out of Scope (v1)

- Email notifications for reminders
- Bulk vCard export
- Proton Contacts API sync (vCard file export is the integration path)
- Activity audit log (who changed what)
- Role-based access control (all users see all data)
