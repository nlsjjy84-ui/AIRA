ALTER TABLE fact DROP CONSTRAINT ck_fact_predicate;
ALTER TABLE fact ADD CONSTRAINT ck_fact_predicate CHECK (
    predicate IN ('REVENUE', 'OPERATING_INCOME', 'REAL_GDP')
);

ALTER TABLE fact ADD CONSTRAINT ck_fact_real_gdp_shape CHECK (
    predicate <> 'REAL_GDP'
    OR (
        event_id IS NULL
        AND value_type = 'NUMBER'
        AND currency_code IS NULL
        AND period_start IS NOT NULL
        AND period_end IS NOT NULL
        AND as_of_at IS NULL
        AND EXTRACT(DAY FROM period_start) = 1
        AND EXTRACT(MONTH FROM period_start) IN (1, 4, 7, 10)
        AND period_end = (period_start + INTERVAL '3 months - 1 day')::date
    )
);

CREATE TABLE fact_statistical_context (
    fact_id uuid PRIMARY KEY,
    statistical_series_id uuid NOT NULL,
    canonical_unit varchar(32) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fact_statistical_context_fact FOREIGN KEY (fact_id)
        REFERENCES fact (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fact_statistical_context_series FOREIGN KEY (statistical_series_id)
        REFERENCES statistical_series (id) ON DELETE RESTRICT,
    CONSTRAINT ck_fact_statistical_context_unit CHECK (
        canonical_unit IN ('KRW_BILLION')
    )
);

CREATE INDEX ix_fact_statistical_context_series
    ON fact_statistical_context (statistical_series_id, fact_id);
CREATE FUNCTION enforce_fact_statistical_context()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    checked_fact_id uuid;
BEGIN
    IF TG_TABLE_NAME = 'fact' THEN
        checked_fact_id := COALESCE(NEW.id, OLD.id);
    ELSE
        checked_fact_id := COALESCE(NEW.fact_id, OLD.fact_id);
    END IF;

    IF EXISTS (
        SELECT 1 FROM fact f
        WHERE f.id = checked_fact_id AND f.predicate = 'REAL_GDP'
    ) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM fact f
            JOIN entity e ON e.id = f.subject_entity_id
            JOIN fact_statistical_context c ON c.fact_id = f.id
            JOIN statistical_series s ON s.id = c.statistical_series_id
            WHERE f.id = checked_fact_id
              AND e.entity_type = 'COUNTRY'
              AND s.subject_entity_id = f.subject_entity_id
              AND s.metric = 'REAL_GDP'
        ) THEN
            RAISE EXCEPTION 'REAL_GDP fact % requires matching statistical context', checked_fact_id
                USING ERRCODE = '23514';
        END IF;
    ELSIF EXISTS (
        SELECT 1 FROM fact_statistical_context c WHERE c.fact_id = checked_fact_id
    ) THEN
        RAISE EXCEPTION 'Non-statistical fact % cannot have statistical context', checked_fact_id
            USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER real_gdp_fact_requires_context
AFTER INSERT OR UPDATE ON fact
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION enforce_fact_statistical_context();

CREATE CONSTRAINT TRIGGER statistical_context_matches_fact
AFTER INSERT OR UPDATE OR DELETE ON fact_statistical_context
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION enforce_fact_statistical_context();

CREATE FUNCTION enforce_statistical_series_fact_context()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM fact_statistical_context c
        JOIN fact f ON f.id = c.fact_id
        JOIN entity e ON e.id = f.subject_entity_id
        WHERE c.statistical_series_id = NEW.id
          AND (
              NEW.subject_entity_id <> f.subject_entity_id
              OR NEW.metric <> 'REAL_GDP'
              OR e.entity_type <> 'COUNTRY'
          )
    ) THEN
        RAISE EXCEPTION 'Statistical series % conflicts with linked fact context', NEW.id
            USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER statistical_series_retains_fact_context
AFTER UPDATE ON statistical_series
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION enforce_statistical_series_fact_context();