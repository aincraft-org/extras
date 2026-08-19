CREATE TABLE IF NOT EXISTS streaks (
      player_id BLOB PRIMARY KEY,
      current_streak INTEGER NOT NULL,
      best_streak INTEGER NOT NULL,
      last_login_date TEXT NOT NULL
    )
