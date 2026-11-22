CREATE TABLE IF NOT EXISTS session_snapshots (
    id UUID PRIMARY KEY,
    room_id VARCHAR(100) NOT NULL,
    room_name VARCHAR(255) NOT NULL,
    invitation_code VARCHAR(50),
    title VARCHAR(200) NOT NULL,
    notes TEXT,
    ai_summary TEXT,
    saved_by_user_id VARCHAR(128) NOT NULL,
    saved_by_name VARCHAR(255),
    owner_user_id VARCHAR(128) NOT NULL,
    participant_user_ids TEXT NOT NULL,
    idea_count INT,
    category_count INT,
    total_votes INT,
    participant_count INT,
    snapshot_payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_session_snapshots_saved_by_created_at
    ON session_snapshots (saved_by_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_session_snapshots_room_id_created_at
    ON session_snapshots (room_id, created_at DESC);
