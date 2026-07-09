CREATE TABLE data_collection_records (
    id UUID PRIMARY KEY,
    category VARCHAR(40) NOT NULL,
    provider VARCHAR(80) NOT NULL,
    endpoint VARCHAR(160) NOT NULL,
    reference_date DATE NOT NULL,
    requested_keys_json JSONB NOT NULL,
    queried_keys_json JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(120),
    error_message VARCHAR(1000),
    payload_json JSONB,
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    took_ms BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_data_collection_records_category CHECK (category IN ('DAILY_HISTORY', 'COMPANY_PROFILE', 'STATISTICS', 'FINANCIAL_DATA', 'BALANCE_SHEET', 'INCOME_STATEMENT', 'CASH_FLOW', 'DIVIDENDS', 'MACRO_SERIES')),
    CONSTRAINT ck_data_collection_records_status CHECK (status IN ('PENDING', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_data_collection_records_took CHECK (took_ms >= 0)
);

CREATE INDEX ix_data_collection_records_reference_category
    ON data_collection_records (reference_date, category);

CREATE INDEX ix_data_collection_records_provider_endpoint
    ON data_collection_records (provider, endpoint, created_at);
