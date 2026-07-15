ALTER TABLE notification_events
    DROP CONSTRAINT ck_notification_events_channel;

UPDATE notification_events
SET channel = 'EMAIL'
WHERE channel = 'EMAIL_SNS';

ALTER TABLE notification_events
    ADD CONSTRAINT ck_notification_events_channel CHECK (channel IN ('EMAIL'));
