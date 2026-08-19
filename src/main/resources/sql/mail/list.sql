SELECT id, recipient, sender_name, body, sent_at, read, attachment
FROM mail
WHERE recipient = ?
ORDER BY sent_at DESC, id DESC
LIMIT ? OFFSET ?
