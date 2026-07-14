ALTER TABLE position_theses
    ALTER COLUMN safety_margin_percent TYPE NUMERIC(19, 6);

ALTER TABLE customer_position_theses
    ALTER COLUMN accepted_safety_margin_percent TYPE NUMERIC(19, 6);

ALTER TABLE allocation_plans
    ALTER COLUMN safety_margin_percent TYPE NUMERIC(19, 6),
    ALTER COLUMN estimated_upside_percent TYPE NUMERIC(19, 6);
