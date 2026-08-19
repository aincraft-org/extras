INSERT INTO friend_requests (requester, target, created_at) VALUES (?, ?, ?)
ON CONFLICT(requester, target) DO UPDATE SET created_at = excluded.created_at
