-- V10: user consent tracking (required before any scan submission)
CREATE TABLE user_consents (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    accepted    BOOLEAN     NOT NULL,
    version     VARCHAR(20) NOT NULL DEFAULT '1.0',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, version)
);
