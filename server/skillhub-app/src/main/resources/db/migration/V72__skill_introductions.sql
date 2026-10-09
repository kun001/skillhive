-- Generation jobs and display text belong to a concrete uploaded version.
CREATE TABLE skill_introduction (
    version_id BIGINT PRIMARY KEY REFERENCES skill_version(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    claim_token VARCHAR(36),
    zh_function_description TEXT,
    zh_usage_instructions TEXT,
    en_function_description TEXT,
    en_usage_instructions TEXT,
    generated_at TIMESTAMPTZ,
    error_code VARCHAR(40)
);
CREATE INDEX idx_skill_introduction_pending ON skill_introduction(status, next_attempt_at);
