CREATE OR REPLACE FUNCTION enforce_fact_statistical_context()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    checked_fact_id uuid;
    previous_fact_id uuid;
BEGIN
    IF TG_TABLE_NAME = 'fact' THEN
        checked_fact_id := COALESCE(NEW.id, OLD.id);
    ELSE
        checked_fact_id := COALESCE(NEW.fact_id, OLD.fact_id);
        IF TG_OP = 'UPDATE' AND OLD.fact_id IS DISTINCT FROM NEW.fact_id THEN
            previous_fact_id := OLD.fact_id;
            IF EXISTS (
                SELECT 1 FROM fact f
                WHERE f.id = previous_fact_id AND f.predicate = 'REAL_GDP'
            ) AND NOT EXISTS (
                SELECT 1
                FROM fact f
                JOIN entity e ON e.id = f.subject_entity_id
                JOIN fact_statistical_context c ON c.fact_id = f.id
                JOIN statistical_series s ON s.id = c.statistical_series_id
                WHERE f.id = previous_fact_id
                  AND e.entity_type = 'COUNTRY'
                  AND s.subject_entity_id = f.subject_entity_id
                  AND s.metric = 'REAL_GDP'
            ) THEN
                RAISE EXCEPTION 'REAL_GDP fact % requires matching statistical context', previous_fact_id
                    USING ERRCODE = '23514';
            END IF;
        END IF;
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
