-- V12: per-user data retention preference and language setting
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS retention_days  INTEGER     DEFAULT NULL,   -- NULL = keep until deleted
    ADD COLUMN IF NOT EXISTS language_code   VARCHAR(10) DEFAULT 'en';
