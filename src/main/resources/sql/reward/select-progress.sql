SELECT amount, claimed FROM daily_progress
WHERE player_id = ? AND day = ? AND criterion_id = ?
