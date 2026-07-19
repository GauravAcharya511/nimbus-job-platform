CREATE TABLE jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type            VARCHAR(100) NOT NULL,
    payload         TEXT,
    status          VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    attempts        INT          NOT NULL DEFAULT 0,
    max_attempts    INT          NOT NULL DEFAULT 3,
    error_message   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,

    CONSTRAINT chk_jobs_status
        CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED')),
    CONSTRAINT chk_jobs_attempts
        CHECK (attempts >= 0 AND attempts <= max_attempts)
);

-- Worker claim path: fetch oldest PENDING jobs first.
CREATE INDEX idx_jobs_status_created ON jobs (status, created_at);
