CREATE TABLE meeting_jobs (
    id UUID PRIMARY KEY,
    access_token_hash VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    source VARCHAR(20) NOT NULL,
    meeting_started_at TIMESTAMPTZ NULL,
    meeting_timezone VARCHAR(100) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    stage VARCHAR(40) NULL,
    source_path TEXT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    duration_ms BIGINT NULL,
    result_json JSONB NULL,
    error_code VARCHAR(60) NULL,
    error_message TEXT NULL,
    completed_at TIMESTAMPTZ NULL,
    expires_at TIMESTAMPTZ NULL,
    CONSTRAINT meeting_jobs_status_check CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT meeting_jobs_stage_check CHECK (stage IS NULL OR stage IN ('PREPARING_MEDIA', 'ANALYZING', 'FINALIZING')),
    CONSTRAINT meeting_jobs_source_check CHECK (source IN ('UPLOAD', 'MICROPHONE')),
    CONSTRAINT meeting_jobs_size_check CHECK (size_bytes > 0),
    CONSTRAINT meeting_jobs_duration_check CHECK (duration_ms IS NULL OR duration_ms > 0)
);

CREATE INDEX idx_meeting_jobs_status ON meeting_jobs (status);
CREATE INDEX idx_meeting_jobs_expires_at ON meeting_jobs (expires_at);
