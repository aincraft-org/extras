INSERT INTO leaderboard_totals (player_id, period, window_key, total, updated_at)
VALUES (?, ?, ?, ?, ?) ON CONFLICT(player_id, period, window_key) DO UPDATE SET
total = total + excluded.total, updated_at = excluded.updated_at
