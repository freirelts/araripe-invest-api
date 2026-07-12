CREATE TABLE user_risk_allocation_settings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users (id),
    capital_base NUMERIC(19, 2) NOT NULL,
    max_allocation_per_asset_percent NUMERIC(10, 6) NOT NULL,
    max_allocation_per_sector_percent NUMERIC(10, 6) NOT NULL,
    tolerated_drawdown_percent NUMERIC(10, 6) NOT NULL,
    minimum_cash_reserve_percent NUMERIC(10, 6) NOT NULL,
    minimum_safety_margin_percent NUMERIC(10, 6) NOT NULL,
    first_tranche_percent NUMERIC(10, 6) NOT NULL,
    second_tranche_percent NUMERIC(10, 6) NOT NULL,
    third_tranche_percent NUMERIC(10, 6) NOT NULL,
    default_stop_percent NUMERIC(10, 6) NOT NULL,
    default_target_return_percent NUMERIC(10, 6) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_user_risk_allocation_settings_user UNIQUE (user_id),
    CONSTRAINT ck_user_risk_allocation_settings_positive CHECK (
        capital_base > 0
        AND max_allocation_per_asset_percent > 0
        AND max_allocation_per_sector_percent > 0
        AND tolerated_drawdown_percent > 0
        AND minimum_cash_reserve_percent >= 0
        AND minimum_safety_margin_percent >= 0
        AND first_tranche_percent >= 0
        AND second_tranche_percent >= 0
        AND third_tranche_percent >= 0
        AND default_stop_percent > 0
        AND default_target_return_percent > 0
    )
);

ALTER TABLE notification_events
    ADD COLUMN read_at TIMESTAMPTZ;
