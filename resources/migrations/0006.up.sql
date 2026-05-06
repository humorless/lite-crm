CREATE TABLE IF NOT EXISTS interest_tag (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  entity_type   TEXT NOT NULL,
  entity_id     INTEGER NOT NULL,
  name          TEXT NOT NULL,
  reminder_date DATE,
  notes         TEXT,
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tag_entity   ON interest_tag (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_tag_reminder ON interest_tag (reminder_date);
