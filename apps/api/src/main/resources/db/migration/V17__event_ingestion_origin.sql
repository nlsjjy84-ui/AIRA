ALTER TABLE event ADD COLUMN ingestion_origin varchar(24) NOT NULL DEFAULT 'LEGACY_UNKNOWN';
ALTER TABLE event ADD CONSTRAINT ck_event_ingestion_origin
    CHECK (ingestion_origin IN ('LIVE', 'BACKFILL', 'LEGACY_UNKNOWN'));
