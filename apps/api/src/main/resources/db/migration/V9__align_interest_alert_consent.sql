-- The audited database contained no existing user_interest rows, so no data
-- rewrite is necessary. Future inserts default to no alert consent.
ALTER TABLE user_interest
    ALTER COLUMN alert_enabled SET DEFAULT false;
