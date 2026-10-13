-- 공부 반: 강사(반장)가 반을 만들고 초대 코드로 수강생을 받아, 같은 회사를 같이 읽는 과제를 낸다.
CREATE TABLE IF NOT EXISTS study_class (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    invite_code VARCHAR(12) NOT NULL UNIQUE,
    sample BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_study_class_owner ON study_class(owner_user_id);
CREATE TABLE IF NOT EXISTS study_class_member (
    class_id UUID NOT NULL REFERENCES study_class(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    display_name VARCHAR(40) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (class_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_study_class_member_user ON study_class_member(user_id);
CREATE TABLE IF NOT EXISTS study_class_assignment (
    id UUID PRIMARY KEY,
    class_id UUID NOT NULL REFERENCES study_class(id) ON DELETE CASCADE,
    company_id UUID NOT NULL,
    company_name VARCHAR(300) NOT NULL,
    title VARCHAR(200) NOT NULL,
    due_date DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_study_class_assignment_class ON study_class_assignment(class_id, created_at);
-- 사실 확인 판정 통계: 문장·사용자는 남기지 않고 날짜·판정·항목별 개수만 센다.
CREATE TABLE IF NOT EXISTS claim_check_stat (
    stat_date DATE NOT NULL,
    verdict VARCHAR(20) NOT NULL,
    item VARCHAR(40) NOT NULL,
    checks INTEGER NOT NULL,
    PRIMARY KEY (stat_date, verdict, item)
);
