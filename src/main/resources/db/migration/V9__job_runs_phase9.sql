CREATE TABLE job_runs (
    id UUID PRIMARY KEY,
    job_name VARCHAR(80) NOT NULL,
    reference_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    trigger VARCHAR(32) NOT NULL,
    requested_by_user_id UUID REFERENCES app_users (id),
    parameters_json JSONB NOT NULL,
    result_summary_json JSONB NOT NULL,
    error_message VARCHAR(1000),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_job_runs_name CHECK (job_name IN ('DAILY_MARKET_DATA_COLLECTION', 'FUNDAMENTAL_DATA_COLLECTION', 'INDICATOR_CALCULATION', 'FILTERS_AND_THESES', 'RANKING', 'AI_CONTEXT_ENRICHMENT', 'PORTFOLIO_SCAN', 'DAILY_NOTIFICATION_DIGEST', 'DAILY_OPERATIONAL_FLOW')),
    CONSTRAINT ck_job_runs_status CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_job_runs_trigger CHECK (trigger IN ('SCHEDULED', 'MANUAL')),
    CONSTRAINT ck_job_runs_completed CHECK ((status = 'RUNNING' AND completed_at IS NULL) OR (status <> 'RUNNING' AND completed_at IS NOT NULL))
);

CREATE UNIQUE INDEX uk_job_runs_running_job_date
    ON job_runs (job_name, reference_date)
    WHERE status = 'RUNNING';

CREATE INDEX ix_job_runs_reference_date
    ON job_runs (reference_date, job_name, started_at);

CREATE INDEX ix_job_runs_requested_by
    ON job_runs (requested_by_user_id, started_at);
