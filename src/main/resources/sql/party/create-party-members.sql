CREATE TABLE IF NOT EXISTS party_members (
              party_id  BLOB NOT NULL REFERENCES parties(party_id) ON DELETE CASCADE,
              member    BLOB NOT NULL,
              joined_at INTEGER NOT NULL,
              PRIMARY KEY (party_id, member)
            )
