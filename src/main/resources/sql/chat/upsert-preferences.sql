INSERT INTO chat_preferences(player_id, active_channel, updated_at) VALUES (?, ?, ?)
ON CONFLICT(player_id) DO UPDATE SET active_channel=excluded.active_channel, updated_at=excluded.updated_at
