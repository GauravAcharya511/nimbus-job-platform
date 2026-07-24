-- CANCELLED is a terminal state for jobs that will not run: a pending job the user
-- withdrew, or a recurring schedule that has been stopped.
ALTER TABLE jobs DROP CONSTRAINT chk_jobs_status;

ALTER TABLE jobs
    ADD CONSTRAINT chk_jobs_status
        CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','CANCELLED'));
