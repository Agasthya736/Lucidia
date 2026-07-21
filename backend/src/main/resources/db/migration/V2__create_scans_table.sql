CREATE TABLE scans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    image_filename VARCHAR(255),
    vision_a_json TEXT,
    vision_b_json TEXT,
    arbitration_json TEXT,
    report_json TEXT,
    verification_json TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    finalized_at TIMESTAMPTZ
);

CREATE INDEX idx_scans_user_id ON scans(user_id);