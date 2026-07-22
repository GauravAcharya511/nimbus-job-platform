-- Cron expression for recurring jobs. NULL means the job runs once.
ALTER TABLE jobs
    ADD COLUMN cron_expression VARCHAR(120);

-- Links each occurrence back to the recurring job that spawned it,
-- so a schedule's execution history can be traced.
ALTER TABLE jobs
    ADD COLUMN parent_job_id UUID;

ALTER TABLE jobs
    ADD CONSTRAINT fk_jobs_parent
        FOREIGN KEY (parent_job_id) REFERENCES jobs(id)
        ON DELETE SET NULL;

CREATE INDEX idx_jobs_parent ON jobs (parent_job_id);
