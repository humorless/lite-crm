CREATE TABLE IF NOT EXISTS log_contact (
  log_id     INTEGER NOT NULL REFERENCES contact_log(id) ON DELETE CASCADE,
  contact_id INTEGER NOT NULL REFERENCES contact(id)    ON DELETE CASCADE,
  PRIMARY KEY (log_id, contact_id)
);
