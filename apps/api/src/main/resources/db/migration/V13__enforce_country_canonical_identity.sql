DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM entity
        WHERE entity_type = 'COUNTRY'
          AND (
              country_code IS NULL
              OR canonical_key <> ('COUNTRY:' || country_code)
              OR market_code IS NOT NULL
              OR symbol IS NOT NULL
          )
    ) THEN
        RAISE EXCEPTION 'Existing COUNTRY rows violate AIRA canonical identity contract';
    END IF;
END $$;

ALTER TABLE entity
    ADD CONSTRAINT ck_entity_country_canonical_identity CHECK (
        entity_type <> 'COUNTRY'
        OR (
            country_code IS NOT NULL
            AND canonical_key = ('COUNTRY:' || country_code)
            AND market_code IS NULL
            AND symbol IS NULL
        )
    );

CREATE UNIQUE INDEX uq_entity_country_code_country
    ON entity (country_code)
    WHERE entity_type = 'COUNTRY';
