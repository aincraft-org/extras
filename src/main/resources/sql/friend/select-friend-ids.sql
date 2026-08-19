SELECT CASE WHEN player_a = ? THEN player_b ELSE player_a END AS friend
FROM friendships WHERE player_a = ? OR player_b = ?
ORDER BY since ASC
