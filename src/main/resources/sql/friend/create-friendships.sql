CREATE TABLE IF NOT EXISTS friendships (
              player_a   BLOB NOT NULL,
              player_b   BLOB NOT NULL,
              since      INTEGER NOT NULL,
              PRIMARY KEY (player_a, player_b)
            )
