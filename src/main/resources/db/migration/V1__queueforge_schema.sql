CREATE TABLE IF NOT EXISTS jobs (
    id BIGSERIAL PRIMARY KEY,
    job_type VARCHAR(100),
    payload TEXT,
    status VARCHAR(30) DEFAULT 'QUEUED',
    priority INTEGER DEFAULT 5,
    attempts INTEGER DEFAULT 0,
    max_attempts INTEGER DEFAULT 5,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    scheduled_at TIMESTAMP WITHOUT TIME ZONE,
    next_retry_at TIMESTAMP WITHOUT TIME ZONE,
    last_error TEXT,
    lease_owner VARCHAR(255),
    lease_expires_at TIMESTAMP WITHOUT TIME ZONE,
    heartbeat_at TIMESTAMP WITHOUT TIME ZONE,
    lease_version BIGINT DEFAULT 0,
    idempotency_key VARCHAR(255),
    request_hash VARCHAR(64),
    row_version BIGINT DEFAULT 0
);

ALTER TABLE jobs ADD COLUMN IF NOT EXISTS job_type VARCHAR(100);
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS payload TEXT;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'QUEUED';
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS priority INTEGER DEFAULT 5;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS attempts INTEGER DEFAULT 0;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS max_attempts INTEGER DEFAULT 5;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS last_error TEXT;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS lease_owner VARCHAR(255);
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS lease_expires_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS heartbeat_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS lease_version BIGINT DEFAULT 0;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255);
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS row_version BIGINT DEFAULT 0;

UPDATE jobs SET job_type = 'UNKNOWN' WHERE job_type IS NULL;
UPDATE jobs SET payload = '' WHERE payload IS NULL;
UPDATE jobs SET status = 'QUEUED' WHERE status IS NULL;
UPDATE jobs SET priority = 5 WHERE priority IS NULL;
UPDATE jobs SET attempts = 0 WHERE attempts IS NULL;
UPDATE jobs SET max_attempts = 5 WHERE max_attempts IS NULL;
UPDATE jobs SET lease_version = 0 WHERE lease_version IS NULL;
UPDATE jobs SET row_version = 0 WHERE row_version IS NULL;

-- Existing pre-Flyway rows used the host application's local timezone.
-- Convert legacy timestamps to UTC before the new UTC-only runtime starts.
UPDATE jobs
SET created_at = (created_at AT TIME ZONE '${legacyTimeZone}') AT TIME ZONE 'UTC';
UPDATE jobs
SET next_retry_at = (next_retry_at AT TIME ZONE '${legacyTimeZone}') AT TIME ZONE 'UTC'
WHERE next_retry_at IS NOT NULL;
UPDATE jobs
SET heartbeat_at = (heartbeat_at AT TIME ZONE '${legacyTimeZone}') AT TIME ZONE 'UTC'
WHERE heartbeat_at IS NOT NULL;
UPDATE jobs SET created_at = clock_timestamp() AT TIME ZONE 'UTC' WHERE created_at IS NULL;

ALTER TABLE jobs ALTER COLUMN job_type TYPE VARCHAR(100);
ALTER TABLE jobs ALTER COLUMN status TYPE VARCHAR(30);
ALTER TABLE jobs ALTER COLUMN job_type SET NOT NULL;
ALTER TABLE jobs ALTER COLUMN payload SET NOT NULL;
ALTER TABLE jobs ALTER COLUMN status SET NOT NULL;
ALTER TABLE jobs ALTER COLUMN priority SET DEFAULT 5;
ALTER TABLE jobs ALTER COLUMN priority SET NOT NULL;
ALTER TABLE jobs ALTER COLUMN attempts SET DEFAULT 0;
ALTER TABLE jobs ALTER COLUMN attempts SET NOT NULL;
ALTER TABLE jobs ALTER COLUMN max_attempts SET DEFAULT 5;
ALTER TABLE jobs ALTER COLUMN max_attempts SET NOT NULL;
ALTER TABLE jobs ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE jobs ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE jobs ALTER COLUMN lease_version SET DEFAULT 0;
ALTER TABLE jobs ALTER COLUMN lease_version SET NOT NULL;
ALTER TABLE jobs ALTER COLUMN row_version SET DEFAULT 0;
ALTER TABLE jobs ALTER COLUMN row_version SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_jobs_idempotency_key
    ON jobs(idempotency_key);
CREATE INDEX IF NOT EXISTS ix_jobs_claim_order
    ON jobs(status, priority DESC, created_at ASC, id ASC);
CREATE INDEX IF NOT EXISTS ix_jobs_retry_due
    ON jobs(next_retry_at) WHERE status = 'FAILED';
CREATE INDEX IF NOT EXISTS ix_jobs_expired_lease
    ON jobs(lease_expires_at) WHERE status = 'PROCESSING';
CREATE INDEX IF NOT EXISTS ix_jobs_dead_created
    ON jobs(created_at DESC) WHERE status = 'DEAD';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_jobs_priority_range') THEN
        ALTER TABLE jobs ADD CONSTRAINT ck_jobs_priority_range CHECK (priority BETWEEN 1 AND 10);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_jobs_attempts_nonnegative') THEN
        ALTER TABLE jobs ADD CONSTRAINT ck_jobs_attempts_nonnegative CHECK (attempts >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_jobs_max_attempts_positive') THEN
        ALTER TABLE jobs ADD CONSTRAINT ck_jobs_max_attempts_positive CHECK (max_attempts BETWEEN 1 AND 20);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_jobs_status_valid') THEN
        ALTER TABLE jobs ADD CONSTRAINT ck_jobs_status_valid
            CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED', 'DEAD', 'CANCELLED'));
    END IF;
END $$;

-- Old versions could leave a PROCESSING row without lease metadata.
UPDATE jobs
SET status = CASE WHEN attempts >= max_attempts THEN 'DEAD' ELSE 'FAILED' END,
    last_error = 'Recovered during QueueForge schema upgrade',
    next_retry_at = CASE WHEN attempts >= max_attempts THEN NULL ELSE CURRENT_TIMESTAMP + INTERVAL '2 seconds' END,
    lease_owner = NULL,
    lease_expires_at = NULL,
    heartbeat_at = NULL,
    lease_version = lease_version + 1,
    row_version = row_version + 1
WHERE status = 'PROCESSING';
