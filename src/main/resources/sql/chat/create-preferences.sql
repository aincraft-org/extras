CREATE TABLE IF NOT EXISTS chat_preferences (
player_id BLOB PRIMARY KEY, active_channel TEXT NOT NULL, updated_at INTEGER NOT NULL)
