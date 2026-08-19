SELECT party_id, inviter, expires_at FROM party_invites
WHERE invitee = ? AND expires_at > ?
ORDER BY expires_at DESC
