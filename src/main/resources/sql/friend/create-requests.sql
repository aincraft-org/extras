CREATE TABLE IF NOT EXISTS friend_requests (
              requester  BLOB NOT NULL,
              target     BLOB NOT NULL,
              created_at INTEGER NOT NULL,
              PRIMARY KEY (requester, target)
            )
