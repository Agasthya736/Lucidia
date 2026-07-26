CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    resource_id UUID NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
    previous_entry_hash VARCHAR(64),
    entry_hash VARCHAR(64) NOT NULL
);

CREATE INDEX idx_audit_log_resource_id ON audit_log(resource_id);
CREATE INDEX idx_audit_log_timestamp ON audit_log(timestamp);