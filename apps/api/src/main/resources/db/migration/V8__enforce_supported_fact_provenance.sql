-- A SUPPORTED fact is only valid at transaction commit when at least one
-- provenance assertion remains. Deferred checking preserves the normal
-- fact-then-first-assertion write order within one transaction.
CREATE FUNCTION enforce_supported_fact_assertion()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    checked_fact_id uuid;
BEGIN
    IF TG_TABLE_NAME = 'fact' THEN
        checked_fact_id := COALESCE(NEW.id, OLD.id);
    ELSE
        checked_fact_id := OLD.fact_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM fact f
        WHERE f.id = checked_fact_id
          AND f.status = 'SUPPORTED'
          AND NOT EXISTS (
              SELECT 1 FROM fact_assertion fa WHERE fa.fact_id = f.id
          )
    ) THEN
        RAISE EXCEPTION 'SUPPORTED fact % requires at least one assertion', checked_fact_id
            USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER supported_fact_requires_assertion
AFTER INSERT OR UPDATE ON fact
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION enforce_supported_fact_assertion();

CREATE CONSTRAINT TRIGGER supported_fact_retains_assertion
AFTER DELETE OR UPDATE ON fact_assertion
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION enforce_supported_fact_assertion();
