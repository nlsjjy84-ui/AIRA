-- 분기 실적용: 분기·반기·3분기 보고서의 누적 손익(1월부터 그 분기 말까지)을 저장한다. 3개월 값은 화면에서 누적끼리 빼서 만든다.
CREATE TABLE IF NOT EXISTS company_quarterly_cumulative (
    corp_code VARCHAR(8) NOT NULL,
    fiscal_year INT NOT NULL,
    period VARCHAR(4) NOT NULL,
    account VARCHAR(32) NOT NULL,
    amount NUMERIC(24,0) NOT NULL,
    receipt_no VARCHAR(20) NOT NULL,
    PRIMARY KEY (corp_code, fiscal_year, period, account)
);

CREATE TABLE IF NOT EXISTS company_quarterly_loaded (
    fiscal_year INT NOT NULL,
    period VARCHAR(4) NOT NULL,
    chunk_start VARCHAR(8) NOT NULL,
    row_count INT NOT NULL,
    loaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (fiscal_year, period, chunk_start)
);
