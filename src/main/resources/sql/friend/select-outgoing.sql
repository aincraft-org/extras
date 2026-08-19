SELECT target, created_at FROM friend_requests WHERE requester = ?
ORDER BY created_at ASC
