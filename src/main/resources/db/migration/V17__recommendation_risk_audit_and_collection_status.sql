ALTER TABLE position_recommendations
    ADD COLUMN price_ceiling NUMERIC(19, 6),
    ADD COLUMN fair_price_estimate NUMERIC(19, 6),
    ADD COLUMN safety_margin_percent NUMERIC(19, 6),
    ADD COLUMN estimated_upside_percent NUMERIC(19, 6),
    ADD COLUMN suggested_quantity INTEGER,
    ADD COLUMN current_asset_exposure_value NUMERIC(19, 2),
    ADD COLUMN current_sector_exposure_value NUMERIC(19, 2),
    ADD COLUMN current_total_exposure_value NUMERIC(19, 2),
    ADD COLUMN available_for_asset NUMERIC(19, 2),
    ADD COLUMN available_for_sector NUMERIC(19, 2),
    ADD COLUMN available_for_cash NUMERIC(19, 2),
    ADD COLUMN allocation_valid BOOLEAN,
    ADD COLUMN allocation_invalid_reason VARCHAR(1000);

ALTER TABLE position_recommendations
    ADD CONSTRAINT ck_position_recommendations_suggested_quantity
        CHECK (suggested_quantity IS NULL OR suggested_quantity >= 0);
