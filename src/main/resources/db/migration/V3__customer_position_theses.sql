CREATE TABLE customer_position_theses (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users (id),
    position_id UUID NOT NULL REFERENCES customer_positions (id),
    asset_id UUID NOT NULL REFERENCES assets (id),
    accepted_thesis_id UUID NOT NULL REFERENCES position_theses (id),
    thesis_type VARCHAR(60) NOT NULL,
    status VARCHAR(32) NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL,
    accepted_score INTEGER NOT NULL,
    accepted_price NUMERIC(19, 6),
    accepted_price_ceiling NUMERIC(19, 6),
    accepted_safety_margin_percent NUMERIC(10, 6),
    rule_version VARCHAR(40) NOT NULL,
    notes VARCHAR(1000),
    closed_at TIMESTAMPTZ,
    exit_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_customer_position_theses_type CHECK (thesis_type IN ('QUALITY_REASONABLE_PRICE', 'SUSTAINABLE_DIVIDENDS', 'PROFITABLE_GROWTH_HEALTHY_TREND')),
    CONSTRAINT ck_customer_position_theses_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT ck_customer_position_theses_score CHECK (accepted_score BETWEEN 0 AND 100)
);

CREATE UNIQUE INDEX uk_customer_position_theses_active_position
    ON customer_position_theses (position_id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_customer_position_theses_asset_type_status
    ON customer_position_theses (asset_id, thesis_type, status);

ALTER TABLE position_recommendations
    ADD COLUMN customer_position_thesis_id UUID REFERENCES customer_position_theses (id),
    ADD COLUMN current_thesis_id UUID REFERENCES position_theses (id),
    ADD COLUMN thesis_type VARCHAR(60),
    ADD CONSTRAINT ck_position_recommendations_thesis_type CHECK (thesis_type IN ('QUALITY_REASONABLE_PRICE', 'SUSTAINABLE_DIVIDENDS', 'PROFITABLE_GROWTH_HEALTHY_TREND'));
