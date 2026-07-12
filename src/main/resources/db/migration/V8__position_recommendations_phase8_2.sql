ALTER TABLE position_recommendations
    DROP CONSTRAINT uk_position_recommendations_idempotency;

ALTER TABLE position_recommendations
    ADD CONSTRAINT uk_position_recommendations_idempotency
        UNIQUE (user_id, position_id, reference_date, rule_version);
