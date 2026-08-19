CREATE TABLE IF NOT EXISTS leaderboard_totals (
      player_id BLOB NOT NULL,
      period TEXT NOT NULL,
      window_key TEXT NOT NULL,
      total INTEGER NOT NULL,
      updated_at INTEGER NOT NULL,
      PRIMARY KEY (player_id, period, window_key)
    )
