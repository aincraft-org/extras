SELECT player_id, total, updated_at FROM leaderboard_totals
WHERE period = ? AND window_key = ? ORDER BY total DESC, updated_at ASC, player_id LIMIT ?
