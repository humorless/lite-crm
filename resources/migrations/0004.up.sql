CREATE TABLE IF NOT EXISTS contact (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  company_id INTEGER REFERENCES company(id) ON DELETE SET NULL,
  name       TEXT NOT NULL,
  department TEXT,
  title      TEXT,
  phone      TEXT,
  phone_ext  TEXT,
  mobile     TEXT,
  email      TEXT,
  notes      TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_contact_company ON contact (company_id);
CREATE INDEX IF NOT EXISTS idx_contact_name    ON contact (name);
