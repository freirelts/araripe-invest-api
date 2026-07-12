ALTER TABLE allocation_plans
    ADD COLUMN max_allocation_per_sector_percent NUMERIC(10, 6),
    ADD COLUMN minimum_cash_reserve_percent NUMERIC(10, 6),
    ADD COLUMN available_for_asset NUMERIC(19, 2),
    ADD COLUMN available_for_sector NUMERIC(19, 2),
    ADD COLUMN fair_price_estimate NUMERIC(19, 6),
    ADD COLUMN safety_margin_percent NUMERIC(10, 6),
    ADD COLUMN estimated_upside_percent NUMERIC(10, 6),
    ADD COLUMN second_tranche_value NUMERIC(19, 2),
    ADD COLUMN third_tranche_value NUMERIC(19, 2),
    ADD COLUMN stop_price NUMERIC(19, 6),
    ADD COLUMN target_price NUMERIC(19, 6);
