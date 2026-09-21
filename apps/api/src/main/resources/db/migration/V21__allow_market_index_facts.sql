ALTER TABLE fact DROP CONSTRAINT ck_fact_predicate;
ALTER TABLE fact ADD CONSTRAINT ck_fact_predicate CHECK (
    predicate IN ('REVENUE', 'OPERATING_INCOME', 'REAL_GDP',
        'OPEN_PRICE', 'HIGH_PRICE', 'LOW_PRICE', 'CLOSE_PRICE',
        'TRADING_VOLUME', 'TRADING_VALUE', 'MARKET_CAP', 'LISTED_SHARES',
        'INDEX_CLOSE', 'INDEX_CHANGE', 'INDEX_CHANGE_RATE')
);

ALTER TABLE fact ADD CONSTRAINT ck_fact_market_index_number CHECK (
    predicate NOT IN ('INDEX_CLOSE', 'INDEX_CHANGE', 'INDEX_CHANGE_RATE')
    OR (value_type = 'NUMBER' AND currency_code IS NULL)
);
