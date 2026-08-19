SELECT requester, created_at FROM friend_requests WHERE target = ?
ORDER BY created_at ASC
