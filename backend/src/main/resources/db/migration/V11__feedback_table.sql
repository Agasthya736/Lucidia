-- V11: user feedback on scan reports
CREATE TABLE scan_feedback (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_id     UUID        NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating      SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_scan_feedback_scan_id ON scan_feedback(scan_id);
CREATE INDEX idx_scan_feedback_user_id ON scan_feedback(user_id);
