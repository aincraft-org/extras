CREATE TABLE IF NOT EXISTS parties (
    party_id BLOB PRIMARY KEY,
    name TEXT,
    leader BLOB NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS party_members (
    party_id BLOB NOT NULL REFERENCES parties(party_id) ON DELETE CASCADE,
    member BLOB NOT NULL,
    joined_at INTEGER NOT NULL,
    PRIMARY KEY (party_id, member)
);

CREATE TABLE IF NOT EXISTS party_invites (
    party_id BLOB NOT NULL REFERENCES parties(party_id) ON DELETE CASCADE,
    invitee BLOB NOT NULL,
    inviter BLOB NOT NULL,
    expires_at INTEGER NOT NULL,
    PRIMARY KEY (party_id, invitee)
);
