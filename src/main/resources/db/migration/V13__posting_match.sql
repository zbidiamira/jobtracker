-- Week 8: the AI's verdict per posting and user, written by postingMatchJob.
-- FAILED rows (unusable AI answer) are kept so the same pair isn't analyzed again on every run.
CREATE TABLE posting_match (
    id          BIGSERIAL   PRIMARY KEY,
    posting_id  BIGINT      NOT NULL REFERENCES job_posting (id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES app_user (id),
    status      VARCHAR(10) NOT NULL CHECK (status IN ('MATCHED', 'FAILED')),
    match_score INT         CHECK (match_score BETWEEN 0 AND 100),
    analysis    JSONB,
    error       VARCHAR(500),
    analyzed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_posting_match UNIQUE (posting_id, user_id)
);

-- "my best matches"
CREATE INDEX idx_posting_match_user_score ON posting_match (user_id, match_score DESC);
