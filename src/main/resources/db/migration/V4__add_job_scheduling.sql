-- Earliest time a job may be attempted. Enables exponential backoff:
-- a failed job is pushed into the future rather than retried immediately.
ALTER TABLE jobs
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Worker claim path:
--   WHERE status = 'PENDING' AND next_attempt_at <= now()
--   ORDER BY next_attempt_at
CREATE INDEX idx_jobs_claim ON jobs (status, next_attempt_at);

-- The old index is now redundant for the claim query.
DROP INDEX IF EXISTS idx_jobs_status_created;
