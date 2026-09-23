ALTER TABLE meeting_jobs DROP CONSTRAINT meeting_jobs_duration_check;

ALTER TABLE meeting_jobs
    ADD CONSTRAINT meeting_jobs_duration_check CHECK (duration_ms IS NULL OR duration_ms >= 0);
