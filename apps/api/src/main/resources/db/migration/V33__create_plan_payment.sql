-- AIRA 플러스 결제(토스페이먼츠 테스트 모드). 주문을 먼저 서버에 만들어 금액을 정해 두고,
-- 결제 승인 때 그 금액과 맞는지 다시 확인한다(화면에서 금액을 바꿔 싸게 결제하는 일 방지).
CREATE TABLE IF NOT EXISTS payment_order (
    order_id VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL,
    plan VARCHAR(16) NOT NULL,
    cycle VARCHAR(8) NOT NULL,
    amount INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    payment_key VARCHAR(200),
    method VARCHAR(40),
    test_mode BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE,
    canceled_at TIMESTAMP WITH TIME ZONE,
    refund_amount INT,
    -- status(READY·DONE·FAILED·CANCELED)와 cycle(month·year)은 서버 코드가 정해진 값만 넣는다.
    CONSTRAINT ck_payment_order_amount CHECK (amount > 0)
);
CREATE INDEX IF NOT EXISTS ix_payment_order_user ON payment_order (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS user_plan (
    user_id UUID PRIMARY KEY,
    plan VARCHAR(16) NOT NULL,
    cycle VARCHAR(8) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    canceled_at TIMESTAMP WITH TIME ZONE
);
