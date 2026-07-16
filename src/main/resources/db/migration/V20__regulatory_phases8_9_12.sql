ALTER TABLE informational_alerts
    ADD COLUMN notification_channel VARCHAR(32) NOT NULL DEFAULT 'EMAIL',
    ADD COLUMN notification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN notification_provider VARCHAR(80),
    ADD COLUMN notification_provider_message_id VARCHAR(160),
    ADD COLUMN notification_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN notification_last_error VARCHAR(1000),
    ADD COLUMN notification_sent_at TIMESTAMPTZ;

ALTER TABLE informational_alerts
    ADD CONSTRAINT ck_informational_alerts_notification_channel CHECK (notification_channel IN ('EMAIL')),
    ADD CONSTRAINT ck_informational_alerts_notification_status CHECK (notification_status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    ADD CONSTRAINT ck_informational_alerts_notification_attempts CHECK (notification_attempt_count >= 0);

CREATE INDEX ix_informational_alerts_digest_candidates
    ON informational_alerts (reference_date, notification_channel, notification_status, user_id, rule_version, created_at);

ALTER TABLE app_users
    ADD COLUMN terms_version_accepted VARCHAR(40),
    ADD COLUMN terms_accepted_at TIMESTAMPTZ;

COMMENT ON COLUMN informational_alerts.notification_status IS
    'Daily e-mail delivery state for the informational alert. Does not represent investment conduct.';

COMMENT ON COLUMN app_users.terms_version_accepted IS
    'Version of educational/informational terms accepted by the user.';
