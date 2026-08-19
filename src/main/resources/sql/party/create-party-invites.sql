CREATE TABLE IF NOT EXISTS party_invites (
              party_id   BLOB NOT NULL REFERENCES parties(party_id) ON DELETE CASCADE,
              invitee    BLOB NOT NULL,
              inviter    BLOB NOT NULL,
              expires_at INTEGER NOT NULL,
              PRIMARY KEY (party_id, invitee)
            )
