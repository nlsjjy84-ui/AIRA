-- 기업 연간 공시에서 당기순이익·자산총계·부채총계·자본총계를 함께 저장할 수 있게 한다.
-- 여러 번 실행해도 같은 결과가 되도록 작성했다(운영 DB에 직접 실행하는 경우 대비).
ALTER TABLE fact DROP CONSTRAINT IF EXISTS ck_fact_predicate;
ALTER TABLE fact ADD CONSTRAINT ck_fact_predicate CHECK (
    predicate IN ('REVENUE', 'OPERATING_INCOME',
        'NET_INCOME', 'TOTAL_ASSETS', 'TOTAL_LIABILITIES', 'TOTAL_EQUITY',
        'REAL_GDP',
        'OPEN_PRICE', 'HIGH_PRICE', 'LOW_PRICE', 'CLOSE_PRICE',
        'TRADING_VOLUME', 'TRADING_VALUE', 'MARKET_CAP', 'LISTED_SHARES',
        'INDEX_CLOSE', 'INDEX_CHANGE', 'INDEX_CHANGE_RATE')
);

ALTER TABLE fact DROP CONSTRAINT IF EXISTS ck_fact_earnings_number;
ALTER TABLE fact ADD CONSTRAINT ck_fact_earnings_number CHECK (
    predicate NOT IN ('REVENUE', 'OPERATING_INCOME',
        'NET_INCOME', 'TOTAL_ASSETS', 'TOTAL_LIABILITIES', 'TOTAL_EQUITY')
    OR (value_type = 'NUMBER' AND currency_code IS NOT NULL)
);
