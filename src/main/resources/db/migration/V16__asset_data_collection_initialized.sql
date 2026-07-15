ALTER TABLE assets
    ADD COLUMN data_collection_initialized BOOLEAN;

UPDATE assets
SET data_collection_initialized = TRUE;

ALTER TABLE assets
    ALTER COLUMN data_collection_initialized SET NOT NULL;

ALTER TABLE assets
    ALTER COLUMN data_collection_initialized SET DEFAULT FALSE;
