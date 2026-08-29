CREATE TABLE IF NOT EXISTS mail (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    recipient TEXT NOT NULL,
    sender_name TEXT NOT NULL,
    body TEXT NOT NULL,
    sent_at INTEGER NOT NULL,
    read INTEGER NOT NULL DEFAULT 0,
    claimed INTEGER NOT NULL DEFAULT 0,
    attachment TEXT
);

CREATE INDEX IF NOT EXISTS idx_mail_recipient_id ON mail(recipient, id);
CREATE INDEX IF NOT EXISTS idx_mail_recipient_read ON mail(recipient, read);
