CREATE TABLE IF NOT EXISTS daily_progress (
      player_id BLOB NOT NULL,
      day TEXT NOT NULL,
      criterion_id TEXT NOT NULL,
      amount INTEGER NOT NULL,
      claimed INTEGER NOT NULL,
      PRIMARY KEY (player_id, day, criterion_id)
    )
