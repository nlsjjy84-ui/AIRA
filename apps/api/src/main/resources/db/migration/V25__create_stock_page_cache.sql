-- 종목 화면(그래프·표)용 요약 저장소. 공식 Fact/Evidence 그래프와는 따로 두며, 값마다 출처(KRX 날짜·OpenDART 접수번호)를 남긴다.
CREATE TABLE stock_price_daily (
    short_code VARCHAR(12) NOT NULL,
    trading_date DATE NOT NULL,
    market VARCHAR(12) NOT NULL,
    close_price NUMERIC(20,2),
    open_price NUMERIC(20,2),
    high_price NUMERIC(20,2),
    low_price NUMERIC(20,2),
    change_rate NUMERIC(10,2),
    trading_volume BIGINT,
    trading_value NUMERIC(24,0),
    market_cap NUMERIC(24,0),
    listed_shares BIGINT,
    PRIMARY KEY (short_code, trading_date)
);

CREATE TABLE stock_price_day_loaded (
    market VARCHAR(12) NOT NULL,
    trading_date DATE NOT NULL,
    row_count INT NOT NULL,
    loaded_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (market, trading_date)
);

CREATE TABLE company_financial_history (
    corp_code VARCHAR(8) NOT NULL,
    fiscal_year INT NOT NULL,
    account VARCHAR(32) NOT NULL,
    amount NUMERIC(24,0) NOT NULL,
    fs_div VARCHAR(4) NOT NULL,
    receipt_no VARCHAR(20) NOT NULL,
    report_year INT NOT NULL,
    PRIMARY KEY (corp_code, fiscal_year, account)
);

CREATE TABLE company_financial_history_loaded (
    report_year INT NOT NULL,
    chunk_start VARCHAR(8) NOT NULL,
    row_count INT NOT NULL,
    loaded_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (report_year, chunk_start)
);

CREATE TABLE company_profile_cache (
    corp_code VARCHAR(8) PRIMARY KEY,
    payload TEXT NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL
);
