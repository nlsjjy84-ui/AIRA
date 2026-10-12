-- 종가 기준 가격 알림: 사용자가 정한 가격에 공식 종가가 닿으면 표시한다(실시간 아님).
CREATE TABLE IF NOT EXISTS user_price_alert (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    company_id UUID NOT NULL,
    company_name VARCHAR(300) NOT NULL,
    short_code VARCHAR(12) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    target NUMERIC(20,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    triggered_at TIMESTAMP WITH TIME ZONE,
    triggered_date DATE,
    triggered_close NUMERIC(20,2)
);
CREATE INDEX IF NOT EXISTS idx_user_price_alert_user ON user_price_alert(user_id);
CREATE INDEX IF NOT EXISTS idx_user_price_alert_open ON user_price_alert(triggered_at, short_code);
