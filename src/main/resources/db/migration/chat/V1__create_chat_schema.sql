CREATE TABLE IF NOT EXISTS chat_preferences (
    player_id BLOB PRIMARY KEY,
    active_channel TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_muted_channels (
    player_id BLOB NOT NULL,
    channel TEXT NOT NULL,
    PRIMARY KEY (player_id, channel),
    FOREIGN KEY (player_id) REFERENCES chat_preferences(player_id) ON DELETE CASCADE
);
