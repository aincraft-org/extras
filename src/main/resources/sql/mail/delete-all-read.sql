DELETE FROM mail WHERE recipient = ? AND read = 1 AND (attachment IS NULL OR claimed = 1)
