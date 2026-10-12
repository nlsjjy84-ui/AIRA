-- 같은 업종 회사 비교용: 회사 개황에서 업종코드·종목코드·법인구분을 따로 꺼내 둔다.
ALTER TABLE company_profile_cache ADD COLUMN IF NOT EXISTS induty_code VARCHAR(10);
ALTER TABLE company_profile_cache ADD COLUMN IF NOT EXISTS stock_code VARCHAR(12);
ALTER TABLE company_profile_cache ADD COLUMN IF NOT EXISTS corp_cls VARCHAR(2);
CREATE INDEX IF NOT EXISTS idx_company_profile_cache_induty ON company_profile_cache(induty_code);
CREATE INDEX IF NOT EXISTS idx_stock_price_daily_date ON stock_price_daily(trading_date);
