-- Associate jobs with the user who submitted them.
-- Nullable because rows may already exist; enforced at the service layer
-- for new submissions.
ALTER TABLE jobs
    ADD COLUMN user_id UUID;

ALTER TABLE jobs
    ADD CONSTRAINT fk_jobs_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE;

-- Supports "list my jobs, newest first".
CREATE INDEX idx_jobs_user_created ON jobs (user_id, created_at DESC);
