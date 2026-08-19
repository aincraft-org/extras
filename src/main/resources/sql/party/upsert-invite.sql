INSERT INTO party_invites (party_id, invitee, inviter, expires_at) VALUES (?, ?, ?, ?)
ON CONFLICT(party_id, invitee) DO UPDATE SET inviter = excluded.inviter,
expires_at = excluded.expires_at
