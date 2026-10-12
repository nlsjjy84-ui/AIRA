-- 종목 메모: 사용자가 종목마다 남기는 자기만의 메모(본인만 봄).
CREATE TABLE IF NOT EXISTS user_stock_note (
    user_id UUID NOT NULL,
    company_id UUID NOT NULL,
    body VARCHAR(4000) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, company_id)
);
