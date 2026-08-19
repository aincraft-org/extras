CREATE TABLE IF NOT EXISTS daily_criteria (
      day TEXT PRIMARY KEY,
      criterion_id TEXT NOT NULL,
      kind TEXT NOT NULL,
      key_value TEXT,
      target INTEGER NOT NULL,
      description TEXT NOT NULL,
      reward_type TEXT NOT NULL,
      reward_payload TEXT NOT NULL,
      reward_amount INTEGER NOT NULL
    )
