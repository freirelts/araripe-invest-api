ALTER TABLE asset_screening_results
    DROP CONSTRAINT IF EXISTS ck_asset_screening_status;

ALTER TABLE asset_screening_results
    DROP COLUMN IF EXISTS status;
