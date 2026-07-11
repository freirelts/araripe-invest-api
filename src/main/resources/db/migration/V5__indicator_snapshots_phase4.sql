ALTER TABLE fundamental_snapshots
    ADD COLUMN calculation_version VARCHAR(40) NOT NULL DEFAULT 'collector-v1',
    ADD COLUMN annual_revenue_growth NUMERIC(10, 6),
    ADD COLUMN quarterly_revenue_growth NUMERIC(10, 6),
    ADD COLUMN annual_earnings_growth NUMERIC(10, 6),
    ADD COLUMN quarterly_earnings_growth NUMERIC(10, 6),
    ADD COLUMN ebitda_growth NUMERIC(10, 6),
    ADD COLUMN net_debt NUMERIC(24, 6),
    ADD COLUMN missing_fields_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN assumptions_json JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE fundamental_snapshots
    DROP CONSTRAINT uk_fundamental_snapshots_asset_period_source;

ALTER TABLE fundamental_snapshots
    ADD CONSTRAINT uk_fundamental_snapshots_asset_period_source_version UNIQUE (asset_id, reference_date, period_type, source, calculation_version);
