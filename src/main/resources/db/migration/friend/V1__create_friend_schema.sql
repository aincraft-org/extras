CREATE TABLE IF NOT EXISTS friend_requests (
    requester BLOB NOT NULL,
    target BLOB NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (requester, target)
);

CREATE TABLE IF NOT EXISTS friendships (
    player_a BLOB NOT NULL,
    player_b BLOB NOT NULL,
    since INTEGER NOT NULL,
    PRIMARY KEY (player_a, player_b)
);
