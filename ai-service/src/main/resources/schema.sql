CREATE TABLE IF NOT EXISTS ai_usage (
    user_id VARCHAR(255) NOT NULL,
    usage_day DATE NOT NULL,
    request_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, usage_day)
);
