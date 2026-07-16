DROP INDEX IF EXISTS uk_informational_alerts_legacy_recommendation;

ALTER TABLE informational_alerts
    DROP CONSTRAINT IF EXISTS informational_alerts_legacy_recommendation_id_fkey;

ALTER TABLE informational_alerts
    DROP COLUMN IF EXISTS legacy_recommendation_id;

DROP TABLE IF EXISTS notification_events;
DROP TABLE IF EXISTS position_recommendations;
DROP TABLE IF EXISTS allocation_plans;
DROP TABLE IF EXISTS user_risk_allocation_settings;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'customer_positions'
          AND column_name = 'stop_price'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'customer_positions'
          AND column_name = 'user_lower_price_threshold'
    ) THEN
        ALTER TABLE customer_positions RENAME COLUMN stop_price TO user_lower_price_threshold;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'customer_positions'
          AND column_name = 'target_price'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'customer_positions'
          AND column_name = 'user_upper_price_threshold'
    ) THEN
        ALTER TABLE customer_positions RENAME COLUMN target_price TO user_upper_price_threshold;
    END IF;
END $$;

ALTER TABLE position_theses
    DROP COLUMN IF EXISTS stop_price,
    DROP COLUMN IF EXISTS target_price;
