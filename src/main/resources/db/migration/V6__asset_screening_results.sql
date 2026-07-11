CREATE TABLE asset_screening_results (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES assets (id),
    reference_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    rule_version VARCHAR(40) NOT NULL,
    failed_filters_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_asset_screening_asset_date_version UNIQUE (asset_id, reference_date, rule_version),
    CONSTRAINT ck_asset_screening_status CHECK (status IN ('ELIGIBLE', 'ELIMINATED'))
);
