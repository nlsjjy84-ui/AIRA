-- 지식이 쌓이는 장치: 공부 노트(저장한 설명)와 배운 용어.
CREATE TABLE IF NOT EXISTS user_study_note (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    company_id UUID,
    company_name VARCHAR(300),
    kind VARCHAR(16) NOT NULL,
    title VARCHAR(300) NOT NULL,
    body VARCHAR(6000) NOT NULL,
    source VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_user_study_note_user ON user_study_note(user_id, created_at);
CREATE TABLE IF NOT EXISTS user_learned_term (
    user_id UUID NOT NULL,
    term VARCHAR(100) NOT NULL,
    learned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, term)
);
