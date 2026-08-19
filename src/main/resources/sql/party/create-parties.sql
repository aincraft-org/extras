CREATE TABLE IF NOT EXISTS parties (
              party_id   BLOB PRIMARY KEY,
              name       TEXT,
              leader     BLOB NOT NULL,
              created_at INTEGER NOT NULL
            )
