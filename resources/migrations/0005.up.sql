CREATE TABLE IF NOT EXISTS contact_log (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  company_id INTEGER NOT NULL REFERENCES company(id) ON DELETE CASCADE,
  date       DATE    NOT NULL,
  content    TEXT    NOT NULL,
  status     TEXT,
  is_pinned  INTEGER NOT NULL DEFAULT 0,
  created_by INTEGER REFERENCES "user"(id),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS log_contact (
  log_id     INTEGER NOT NULL REFERENCES contact_log(id) ON DELETE CASCADE,
  contact_id INTEGER NOT NULL REFERENCES contact(id)    ON DELETE CASCADE,
  PRIMARY KEY (log_id, contact_id)
);

CREATE INDEX IF NOT EXISTS idx_log_company ON contact_log (company_id);
CREATE INDEX IF NOT EXISTS idx_log_date    ON contact_log (date);
