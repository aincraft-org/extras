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
);

CREATE TABLE IF NOT EXISTS daily_progress (
    player_id BLOB NOT NULL,
    day TEXT NOT NULL,
    criterion_id TEXT NOT NULL,
    amount INTEGER NOT NULL,
    claimed INTEGER NOT NULL,
    PRIMARY KEY (player_id, day, criterion_id)
);

CREATE TABLE IF NOT EXISTS streaks (
    player_id BLOB PRIMARY KEY,
    current_streak INTEGER NOT NULL,
    best_streak INTEGER NOT NULL,
    last_login_date TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS leaderboard_totals (
    player_id BLOB NOT NULL,
    period TEXT NOT NULL,
    window_key TEXT NOT NULL,
    total INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (player_id, period, window_key)
);
