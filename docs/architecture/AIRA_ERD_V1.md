# AIRA ERD v1

- 상태: Frozen
- 버전: 1.0
- 기준일: 2026-08-14
- DBMS 기준: PostgreSQL
- 상위 문서: [AIRA Architecture v1](AIRA_ARCHITECTURE_V1.md)
- 관련 결정: [ADR-001](../adr/ADR-001-event-centered-model.md), [ADR-002](../adr/ADR-002-evidence-assessment-separation.md), [ADR-003](../adr/ADR-003-data-minimization.md), [ADR-004](../adr/ADR-004-ai-token-cost-control.md), [ADR-005](../adr/ADR-005-session-identifier-rotation.md)

## 1. 목적과 설계 원칙

이 문서는 AIRA v1의 논리·물리 ERD 기준을 확정한다. 실제 DDL, migration, Java Entity와 API 계약은 이 문서를 근거로 별도 설계한다.

핵심 원칙은 다음과 같다.

- Event를 정보 모델의 중심으로 사용한다.
- 외부에서 검증 가능한 Evidence와 AIRA의 Assessment를 분리한다.
- Event에는 매수·매도 또는 단일 positive·negative 값을 저장하지 않는다.
- Impact는 Entity별 영향 요인, 전달 경로, 시간 범위와 불확실성을 표현한다.
- 사용자 데이터와 인증정보를 분리하고 개인정보를 최소 수집한다.
- 공통 Event와 Assessment를 사용자별로 복제하지 않는다.
- AI 실행과 비용을 추적하되 Secret과 인증정보를 저장하지 않는다.
- v1에 필요하지 않은 subtype, 공급자별 테이블과 마이크로서비스용 구조를 선행 생성하지 않는다.

## 2. 공통 PostgreSQL 규칙

- 단독 엔티티 PK는 `uuid`를 사용한다. UUID 생성 방식은 암호학적으로 안전하고 예측 불가능해야 한다.
- 시각은 `timestamptz`로 UTC 저장한다.
- 비용은 `numeric(18,8)`, 토큰 수는 `bigint`, 해시와 fingerprint는 `bytea`를 사용한다.
- 상태값은 v1에서 `varchar + CHECK`를 우선한다. PostgreSQL native enum은 값 변경 부담 때문에 사용하지 않는다.
- 변경 가능한 엔티티에는 `created_at`, `updated_at`을 둔다.
- FK 삭제 정책은 공개 시장 데이터에 기본 `RESTRICT`, 사용자 종속 데이터에 명시적 삭제 정책을 적용한다.
- 부분 인덱스에는 `now()`처럼 시간에 따라 결과가 바뀌는 조건을 사용하지 않는다.

## 3. 전체 관계

```text
Source 1 ── N Evidence
Evidence N ── M Event              (event_evidence)
Event N ── M Entity                (event_entity)

Event 1 ── N Assessment
Assessment N ── M Evidence         (assessment_evidence)
Assessment 1 ── N Impact
Entity 1 ── N Impact

AppUser 1 ── 1 AuthenticationCredential
AppUser 1 ── 0..1 RecoveryEmail
AppUser 1 ── N RecoveryEmailVerification
RecoveryEmail 1 ── N PasswordResetToken
AppUser 1 ── N UserSession
AppUser N ── M Entity              (user_interest)

Event / Assessment ── N AIExecution
AIExecution 0..1 ── N AIExecution  (reuse lineage)

AppUser 1 ── N Alert
AppUser 1 ── N Briefing
Briefing N ── M Assessment         (briefing_item)
```

## 4. 최종 테이블

### 4.1 `source`

정보를 제공하거나 발행한 주체다. 단일 절대 신뢰도 점수는 저장하지 않는다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | `uuid` | PK |
| `source_type` | `varchar(32)` | NOT NULL, CHECK |
| `name` | `varchar(200)` | NOT NULL |
| `canonical_domain` | `varchar(255)` | NULL 허용 |
| `external_key` | `varchar(200)` | NULL 허용 |
| `active` | `boolean` | NOT NULL |
| `created_at` | `timestamptz` | NOT NULL |
| `updated_at` | `timestamptz` | NOT NULL |

후보 제약과 인덱스:

- `UNIQUE (source_type, external_key)` where `external_key IS NOT NULL`
- `canonical_domain`은 URL 정규화 정책 확정 후 조건부 unique를 검토한다.

### 4.2 `evidence`

외부에서 검증 가능한 근거다. AI 생성 문장은 Evidence가 될 수 없다. 전체 원문을 무조건 DB에 복제하지 않는다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | `uuid` | PK |
| `source_id` | `uuid` | NOT NULL, FK → `source.id` |
| `evidence_type` | `varchar(32)` | NOT NULL, CHECK |
| `external_id` | `varchar(255)` | NULL 허용 |
| `original_url` | `text` | NOT NULL |
| `title` | `text` | NULL 허용 |
| `content_hash` | `bytea` | NOT NULL |
| `locator` | `text` | 원문 페이지·문단·섹션 |
| `excerpt` | `text` | 판단에 필요한 최소 구간만 저장 |
| `published_at` | `timestamptz` | NULL 허용 |
| `collected_at` | `timestamptz` | NOT NULL |
| `revision` | `integer` | NOT NULL, `>= 1` |
| `status` | `varchar(24)` | NOT NULL, CHECK |

후보 제약과 인덱스:

- `UNIQUE (source_id, external_id, revision)` where `external_id IS NOT NULL`
- `INDEX (content_hash)`
- `INDEX (source_id, published_at DESC)`
- `INDEX (collected_at DESC)`

`original_url` 단독 unique는 URL 변형과 원문 수정 때문에 강제하지 않는다. `content_hash`는 중복 탐지와 AI 입력 재사용에 활용하되, 해시 충돌이나 동일 문구 재사용을 이유로 자동 Event 병합하지 않는다.

### 4.3 `event`

여러 Evidence를 하나의 사건 단위로 통합한 핵심 엔티티다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | `uuid` | PK |
| `event_type` | `varchar(32)` | NOT NULL, CHECK |
| `title` | `text` | NOT NULL, 중립적 사건 제목 |
| `occurred_at` | `timestamptz` | NULL 허용 |
| `occurred_until` | `timestamptz` | NULL 허용 |
| `first_observed_at` | `timestamptz` | NOT NULL |
| `last_observed_at` | `timestamptz` | NOT NULL |
| `status` | `varchar(24)` | NOT NULL, CHECK |
| `dedup_key` | `bytea` | 결정론적 키가 있을 때만 저장 |
| `superseded_by_event_id` | `uuid` | self FK, NULL 허용 |
| `merge_reason` | `text` | 병합 근거 |
| `merged_at` | `timestamptz` | NULL 허용 |
| `created_at` | `timestamptz` | NOT NULL |
| `updated_at` | `timestamptz` | NOT NULL |

후보 제약과 인덱스:

- `UNIQUE (dedup_key)` where `dedup_key IS NOT NULL`
- `INDEX (event_type, occurred_at DESC)`
- `INDEX (status, last_observed_at DESC)`
- `INDEX (superseded_by_event_id)`
- `occurred_until >= occurred_at` CHECK when both values exist
- Event에는 direction, positive, negative, 호재·악재 또는 매수·매도 컬럼을 두지 않는다.

### 4.4 `event_evidence`

Event와 Evidence의 다대다 관계다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `event_id` | `uuid` | PK 일부, FK → `event.id` |
| `evidence_id` | `uuid` | PK 일부, FK → `evidence.id` |
| `relation_type` | `varchar(24)` | NOT NULL, CHECK |
| `linked_at` | `timestamptz` | NOT NULL |

- PK: `(event_id, evidence_id)`
- 역방향 인덱스: `(evidence_id, event_id)`

### 4.5 `entity`

기업, 증권·종목, 산업, 시장, 국가와 원자재 등 공개 시장 대상이다. 일반 사용자는 Entity로 만들지 않는다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | `uuid` | PK |
| `entity_type` | `varchar(24)` | NOT NULL, CHECK |
| `canonical_name` | `varchar(300)` | NOT NULL |
| `canonical_key` | `varchar(300)` | NOT NULL, UNIQUE |
| `market_code` | `varchar(32)` | 종목 등 해당 유형에서만 사용 |
| `symbol` | `varchar(64)` | 종목 등 해당 유형에서만 사용 |
| `country_code` | `char(2)` | ISO 3166-1 alpha-2, 해당 시 |
| `active` | `boolean` | NOT NULL |
| `created_at` | `timestamptz` | NOT NULL |
| `updated_at` | `timestamptz` | NOT NULL |

후보 제약과 인덱스:

- `UNIQUE (entity_type, market_code, symbol)` where `market_code` and `symbol` are not null
- 유형 전용 nullable 컬럼이 지속적으로 5~7개 이상 증가하거나 유형별 필수 제약·수명주기가 달라지면 필요한 subtype만 분리한다.
- 미래 확장만을 이유로 v1에서 subtype 테이블을 만들지 않는다.

### 4.6 `event_entity`

Event와 관련 Entity의 다대다 관계다. 관련성은 영향 방향을 의미하지 않는다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `event_id` | `uuid` | PK 일부, FK → `event.id` |
| `entity_id` | `uuid` | PK 일부, FK → `entity.id` |
| `relation_type` | `varchar(24)` | NOT NULL, CHECK |
| `relevance` | `varchar(16)` | NOT NULL, CHECK |
| `created_at` | `timestamptz` | NOT NULL |

- PK: `(event_id, entity_id)`
- 역방향 인덱스: `(entity_id, event_id)`

### 4.7 `assessment`

Event의 중요성, 가능한 영향과 불확실성을 분석한 결과다. 사실 원문과 분리한다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | `uuid` | PK |
| `event_id` | `uuid` | NOT NULL, FK → `event.id` |
| `analysis_version` | `varchar(64)` | NOT NULL |
| `method` | `varchar(24)` | NOT NULL, CHECK |
| `importance` | `varchar(16)` | NOT NULL, CHECK |
| `summary` | `text` | NOT NULL |
| `confidence` | `varchar(16)` | NOT NULL, CHECK |
| `uncertainty` | `text` | NULL 허용 |
| `time_horizon` | `varchar(24)` | NOT NULL, CHECK |
| `status` | `varchar(24)` | NOT NULL, CHECK |
| `input_fingerprint` | `bytea` | NOT NULL |
| `completed_at` | `timestamptz` | NULL 허용 |
| `supersedes_assessment_id` | `uuid` | self FK, NULL 허용 |
| `created_at` | `timestamptz` | NOT NULL |
| `updated_at` | `timestamptz` | NOT NULL |

후보 제약과 인덱스:

- `UNIQUE (event_id, analysis_version, input_fingerprint)`
- `INDEX (event_id, status, completed_at DESC)`
- Assessment를 매수·매도 지시로 변환하지 않는다.

### 4.8 `assessment_evidence`

Assessment가 실제 사용한 Evidence를 추적한다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `assessment_id` | `uuid` | PK 일부, FK → `assessment.id` |
| `evidence_id` | `uuid` | PK 일부, FK → `evidence.id` |
| `usage_type` | `varchar(24)` | NOT NULL, CHECK |
| `created_at` | `timestamptz` | NOT NULL |

- PK: `(assessment_id, evidence_id)`
- 역방향 인덱스: `(evidence_id, assessment_id)`

### 4.9 `impact`

특정 Assessment가 특정 Entity에 어떤 요인과 경로로 영향을 줄 가능성이 있는지 표현한다. 단순 호재·악재 판정 테이블이 아니다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | `uuid` | PK |
| `assessment_id` | `uuid` | NOT NULL, FK → `assessment.id` |
| `entity_id` | `uuid` | NOT NULL, FK → `entity.id` |
| `sequence_no` | `smallint` | NOT NULL, `> 0` |
| `factor` | `text` | NOT NULL, 영향 요인 |
| `transmission_path` | `text` | NOT NULL, 영향 전달 경로 |
| `rationale` | `text` | NOT NULL, 판단 설명 |
| `direction` | `varchar(16)` | NOT NULL, 보조 데이터 |
| `time_horizon` | `varchar(24)` | NOT NULL, CHECK |
| `confidence` | `varchar(16)` | NOT NULL, CHECK |
| `uncertainty` | `text` | NULL 허용 |
| `created_at` | `timestamptz` | NOT NULL |
| `updated_at` | `timestamptz` | NOT NULL |

규칙:

- direction: `POSITIVE`, `NEGATIVE`, `NEUTRAL`, `MIXED`, `UNKNOWN`
- `UNKNOWN`은 정상적인 분석 결과다.
- 핵심 정보는 `factor`, `transmission_path`, `rationale`, `time_horizon`, `confidence`, `uncertainty`와 근거 관계다.
- 근거는 `impact → assessment → assessment_evidence → evidence`로 추적한다.
- 동일 Assessment와 Entity에 여러 영향 요인을 허용한다.
- 사용자 UI에서 direction만으로 호재·악재 딱지를 표시하는 것을 전제로 하지 않는다.

후보 제약과 인덱스:

- `UNIQUE (assessment_id, entity_id, sequence_no)`
- `INDEX (entity_id, time_horizon)`
- `INDEX (assessment_id)`

### 4.10 `ai_execution`

AI 실행과 결과 재사용, 토큰 및 비용을 추적하는 운영 데이터다. 사용자와 직접 연결하지 않는다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | `uuid` | PK |
| `provider_key` | `varchar(64)` | NOT NULL, 공급자 중립 코드 |
| `model_key` | `varchar(128)` | NOT NULL |
| `task_type` | `varchar(64)` | NOT NULL |
| `prompt_version` | `varchar(64)` | NOT NULL |
| `event_id` | `uuid` | FK → `event.id`, NULL 허용 |
| `assessment_id` | `uuid` | FK → `assessment.id`, NULL 허용 |
| `input_fingerprint` | `bytea` | NOT NULL |
| `cache_key` | `bytea` | NOT NULL |
| `cache_hit` | `boolean` | NOT NULL |
| `reused_execution_id` | `uuid` | self FK, NULL 허용 |
| `input_tokens` | `bigint` | NULL 허용, `>= 0` |
| `output_tokens` | `bigint` | NULL 허용, `>= 0` |
| `estimated_cost` | `numeric(18,8)` | NULL 허용, `>= 0` |
| `actual_cost` | `numeric(18,8)` | NULL 허용, `>= 0` |
| `currency_code` | `char(3)` | NULL 허용 |
| `latency_ms` | `integer` | NULL 허용, `>= 0` |
| `status` | `varchar(16)` | NOT NULL, CHECK |
| `error_code` | `varchar(64)` | 비민감 오류 분류만 저장 |
| `started_at` | `timestamptz` | NOT NULL |
| `completed_at` | `timestamptz` | NULL 허용 |

후보 인덱스:

- `(cache_key, status, completed_at DESC)`
- `(cache_key, completed_at DESC)` where `status = 'SUCCEEDED'`
- `(task_type, started_at DESC)`
- `(provider_key, model_key, started_at DESC)`
- `(assessment_id)` 및 `(reused_execution_id)`

`cache_key`는 실패 후 재시도와 모델 비교를 위해 unique로 만들지 않는다. API Key, 인증 토큰, Secret, 불필요한 프롬프트·응답 원문을 저장하지 않는다.

### 4.11 `app_user`

최소한의 사용자 계정 정보다. PostgreSQL 예약·내장 개념과 혼동하지 않도록 물리 이름을 `app_user`로 한다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | `uuid` | PK, 내부 식별자 |
| `nickname` | `varchar(20)` | NOT NULL, 가입 후 변경 금지 |
| `nickname_normalized` | `varchar(20)` | NOT NULL, UNIQUE, 변경 금지 |
| `status` | `varchar(16)` | NOT NULL, CHECK |
| `locale` | `varchar(16)` | NULL 허용 |
| `timezone` | `varchar(64)` | NULL 허용 |
| `created_at` | `timestamptz` | NOT NULL |
| `updated_at` | `timestamptz` | NOT NULL |
| `deleted_at` | `timestamptz` | NULL 허용 |

nickname 규칙:

- 3~20자
- 한글 완성형 음절, 영문자, 숫자만 허용
- 첫 글자는 숫자일 수 없음
- 공백과 특수문자 금지
- 애플리케이션에서 Unicode NFKC 정규화 및 영문 case-fold를 적용해 `nickname_normalized` 생성
- 정규화 기준 중복 금지
- 예약어 `admin`, `administrator`, `관리자`, `운영자` 및 향후 정책 목록 금지
- 가입 완료 후 `nickname`, `nickname_normalized` 변경 금지

DB CHECK 후보:

```text
nickname ~ '^[가-힣A-Za-z][가-힣A-Za-z0-9]{2,19}$'
nickname_normalized NOT IN ('admin', 'administrator', '관리자', '운영자')
```

Unicode 정규화 자체는 PostgreSQL 정규식에 의존하지 않고 애플리케이션과 테스트에서 동일 알고리즘을 강제한다.

### 4.12 `authentication_credential`

User와 분리된 v1 비밀번호 인증정보다. Google, Apple과 OAuth는 포함하지 않는다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | `uuid` | PK |
| `user_id` | `uuid` | NOT NULL, FK → `app_user.id`, UNIQUE |
| `password_hash` | `text` | NOT NULL, Argon2id 인코딩 문자열 |
| `password_changed_at` | `timestamptz` | NOT NULL |
| `status` | `varchar(16)` | NOT NULL, CHECK |
| `failed_attempts` | `integer` | NOT NULL, `>= 0` |
| `locked_until` | `timestamptz` | NULL 허용 |
| `created_at` | `timestamptz` | NOT NULL |
| `updated_at` | `timestamptz` | NOT NULL |

규칙:

- 비밀번호 원문을 저장하거나 로그에 기록하지 않는다.
- `password_hash`에는 Argon2id 알고리즘, 버전, salt와 파라미터가 포함된 표준 인코딩 문자열을 저장한다.
- Argon2id 메모리, 반복 및 병렬도 파라미터를 문서에서 임의 숫자로 고정하지 않는다.
- 배포 환경에서 보안 기준과 허용 가능한 로그인 지연을 만족하도록 benchmark한 뒤 운영 설정으로 확정한다.
- pepper를 도입할 경우 DB가 아닌 Secret 관리 영역에 둔다.

### 4.13 `recovery_email`

검증 완료된 계정 복구 전용 이메일이다. 로그인 ID가 아니며 마케팅과 프로파일링에 사용할 수 없다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | `uuid` | PK |
| `user_id` | `uuid` | NOT NULL, FK → `app_user.id` |
| `purpose` | `varchar(32)` | NOT NULL, `ACCOUNT_RECOVERY` 고정 |
| `email_ciphertext` | `bytea` | NOT NULL, 보호된 이메일 |
| `email_lookup_hash` | `bytea` | NOT NULL, keyed hash |
| `encryption_key_version` | `smallint` | NOT NULL |
| `verified_at` | `timestamptz` | NOT NULL |
| `created_at` | `timestamptz` | NOT NULL |
| `updated_at` | `timestamptz` | NOT NULL |
| `deleted_at` | `timestamptz` | NULL 허용 |

후보 제약과 인덱스:

- 활성 이메일 기준 `UNIQUE (user_id)`
- 활성 이메일 기준 `UNIQUE (email_lookup_hash)`
- 한 복구 이메일은 한 AIRA 계정에만 연결한다.
- 복구 이메일 미등록 계정을 허용한다.
- 암호화·keyed hash 키는 DB나 소스코드에 저장하지 않는다.

### 4.14 `recovery_email_verification`

신규 등록 또는 이메일 변경 시 검증 전 후보 이메일을 관리한다. 기존 검증 이메일은 새 이메일 검증 성공 전까지 유지한다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | `uuid` | PK |
| `user_id` | `uuid` | NOT NULL, FK → `app_user.id` |
| `candidate_email_ciphertext` | `bytea` | NOT NULL |
| `candidate_email_lookup_hash` | `bytea` | NOT NULL |
| `encryption_key_version` | `smallint` | NOT NULL |
| `token_hash` | `bytea` | NOT NULL, UNIQUE |
| `requested_at` | `timestamptz` | NOT NULL |
| `expires_at` | `timestamptz` | NOT NULL |
| `verified_at` | `timestamptz` | NULL 허용 |
| `invalidated_at` | `timestamptz` | NULL 허용 |

규칙:

- 검증 token 원문은 저장하거나 로그에 기록하지 않는다.
- 검증 성공 트랜잭션에서 `recovery_email.email_lookup_hash` unique를 다시 확인한다.
- 새 이메일 검증 성공 전에는 기존 복구 이메일을 교체하지 않는다.
- 성공 또는 새 요청 발급 시 이전 미사용 검증 요청을 무효화한다.

후보 인덱스:

- `(user_id, expires_at)`
- `(candidate_email_lookup_hash)`
- `(token_hash)` unique

### 4.15 `password_reset_token`

복구 이메일로 발급한 암호학적으로 안전한 일회용 비밀번호 재설정 token을 관리한다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | `uuid` | PK |
| `recovery_email_id` | `uuid` | NOT NULL, FK → `recovery_email.id` |
| `token_hash` | `bytea` | NOT NULL, UNIQUE |
| `requested_at` | `timestamptz` | NOT NULL |
| `expires_at` | `timestamptz` | NOT NULL |
| `used_at` | `timestamptz` | NULL 허용 |
| `invalidated_at` | `timestamptz` | NULL 허용 |
| `invalid_reason` | `varchar(32)` | NULL 허용 |

규칙:

- token은 암호학적으로 안전한 난수로 생성한다.
- 원문 token은 DB와 로그에 저장하지 않는다.
- `expires_at = requested_at + 30분`을 강제한다.
- 한 번 사용한 token은 즉시 재사용 불가능하게 한다.
- 새 reset token 발급 시 같은 사용자의 기존 미사용 token을 무효화한다.
- 재설정 성공 트랜잭션에서 `used_at` 기록, credential 갱신 및 해당 사용자의 모든 활성 session 폐기를 함께 수행한다.
- 세션 폐기 사유는 `PASSWORD_RESET`이다.

후보 인덱스:

- `(recovery_email_id, expires_at)`
- `(token_hash)` unique

### 4.16 `user_session`

서버 관리형 opaque session이다. 내부 `user_id`와 외부 token을 분리한다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | `uuid` | PK, 내부 세션 ID |
| `user_id` | `uuid` | NOT NULL, FK → `app_user.id` |
| `credential_id` | `uuid` | NOT NULL, FK → `authentication_credential.id` |
| `token_hash` | `bytea` | NOT NULL, UNIQUE |
| `rotated_from_session_id` | `uuid` | self FK, NULL 허용 |
| `issued_at` | `timestamptz` | NOT NULL |
| `last_seen_at` | `timestamptz` | NOT NULL |
| `idle_expires_at` | `timestamptz` | NOT NULL |
| `absolute_expires_at` | `timestamptz` | NOT NULL |
| `revoked_at` | `timestamptz` | NULL 허용 |
| `revoke_reason` | `varchar(32)` | NULL 허용 |

규칙:

- 외부 token은 암호학적으로 안전한 랜덤 opaque value다.
- DB에는 token hash만 저장한다.
- idle timeout은 마지막 유효 사용으로부터 30분이다.
- absolute timeout은 최초 발급으로부터 12시간이며 연장하지 않는다.
- 유효성은 `revoked_at IS NULL`, 현재 시각이 `idle_expires_at`과 `absolute_expires_at`보다 이전인지를 모두 검사한다.
- 동시 로그인을 허용한다.
- 로그인, 재인증 등 보안 경계에서 session identifier를 회전한다.
- 회전 시 이전 session을 `ROTATED`로 폐기하고 새 행이 `rotated_from_session_id`로 연결한다.
- logout 시 `LOGOUT`, 비밀번호 재설정 시 `PASSWORD_RESET`로 폐기한다.
- 만료 session은 인증에 사용할 수 없으며 보존기간 후 삭제한다.
- 원문 token, Wi-Fi/Bluetooth 정보와 영구적인 device identifier를 저장하지 않는다.
- 브라우저에는 `HttpOnly`, `Secure`, 적절한 `SameSite` cookie로 전달한다. cookie 속성은 DB 컬럼이 아니라 HTTP 보안 설정이다.

후보 제약과 인덱스:

- `UNIQUE (token_hash)`
- `UNIQUE (rotated_from_session_id)` where not null
- `INDEX (user_id, absolute_expires_at)`
- `INDEX (user_id, idle_expires_at)` where `revoked_at IS NULL`
- `idle_expires_at <= absolute_expires_at` CHECK
- `absolute_expires_at = issued_at + interval '12 hours'` CHECK 후보

### 4.17 `user_interest`

사용자와 공개 시장 Entity의 관심 관계다. 공통 분석을 사용자별로 복제하지 않는다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | `uuid` | PK |
| `user_id` | `uuid` | NOT NULL, FK → `app_user.id` |
| `entity_id` | `uuid` | NOT NULL, FK → `entity.id` |
| `interest_level` | `varchar(16)` | NULL 허용 |
| `alert_enabled` | `boolean` | NOT NULL |
| `created_at` | `timestamptz` | NOT NULL |
| `updated_at` | `timestamptz` | NOT NULL |

- `UNIQUE (user_id, entity_id)`
- 역방향 인덱스: `(entity_id, user_id)`

### 4.18 `alert`

공통 Assessment와 Impact를 사용자에게 전달하는 개별 알림이다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | `uuid` | PK |
| `user_id` | `uuid` | NOT NULL, FK → `app_user.id` |
| `assessment_id` | `uuid` | NOT NULL, FK → `assessment.id` |
| `impact_id` | `uuid` | FK → `impact.id`, NULL 허용 |
| `policy_version` | `varchar(64)` | NOT NULL |
| `reason_code` | `varchar(64)` | NOT NULL |
| `dedup_key` | `bytea` | NOT NULL |
| `status` | `varchar(16)` | NOT NULL, CHECK |
| `scheduled_at` | `timestamptz` | NULL 허용 |
| `sent_at` | `timestamptz` | NULL 허용 |
| `failure_code` | `varchar(64)` | NULL 허용 |
| `created_at` | `timestamptz` | NOT NULL |
| `updated_at` | `timestamptz` | NOT NULL |

- `UNIQUE (user_id, dedup_key)`
- `INDEX (user_id, status, scheduled_at)`
- `INDEX (assessment_id)`
- 새 정보가 있다는 이유만으로 Alert를 생성하지 않는다.

### 4.19 `briefing`

특정 기간에 공통 Assessment를 선택·정렬해 제공하는 사용자 Briefing이다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | `uuid` | PK |
| `user_id` | `uuid` | NOT NULL, FK → `app_user.id` |
| `briefing_type` | `varchar(24)` | NOT NULL, CHECK |
| `period_start` | `timestamptz` | NOT NULL |
| `period_end` | `timestamptz` | NOT NULL |
| `policy_version` | `varchar(64)` | NOT NULL |
| `status` | `varchar(16)` | NOT NULL, CHECK |
| `title` | `text` | NOT NULL |
| `dedup_key` | `bytea` | NOT NULL |
| `generated_at` | `timestamptz` | NULL 허용 |
| `delivered_at` | `timestamptz` | NULL 허용 |
| `created_at` | `timestamptz` | NOT NULL |
| `updated_at` | `timestamptz` | NOT NULL |

- `UNIQUE (user_id, dedup_key)`
- `UNIQUE (user_id, briefing_type, period_start, period_end)` 후보
- `period_end > period_start` CHECK

### 4.20 `briefing_item`

Briefing과 재사용 가능한 공통 Assessment의 연결이다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `briefing_id` | `uuid` | PK 일부, FK → `briefing.id` |
| `assessment_id` | `uuid` | PK 일부, FK → `assessment.id` |
| `impact_id` | `uuid` | FK → `impact.id`, NULL 허용 |
| `display_order` | `smallint` | NOT NULL, `> 0` |
| `reason_code` | `varchar(64)` | NOT NULL |
| `created_at` | `timestamptz` | NOT NULL |

- PK: `(briefing_id, assessment_id)`
- `UNIQUE (briefing_id, display_order)`
- 역방향 인덱스: `(assessment_id, briefing_id)`
- 동일 내용을 사용자별 AI 호출로 다시 생성하지 않는다.

## 5. 상태값 후보

- `source_type`: `NEWS`, `REGULATOR`, `EXCHANGE`, `COMPANY_IR`, `GOVERNMENT`, `OTHER`
- `evidence_type`: `ARTICLE`, `DISCLOSURE`, `IR`, `PRESS_RELEASE`, `OFFICIAL_DATA`, `OTHER`
- `evidence_status`: `ACTIVE`, `UPDATED`, `RETRACTED`, `UNAVAILABLE`
- `event_type`: `EARNINGS`, `DISCLOSURE`, `BUSINESS`, `GOVERNANCE`, `POLICY_REGULATION`, `RISK`, `MARKET`
- `event_status`: `CANDIDATE`, `CONFIRMED`, `MERGED`, `DISCARDED`
- `entity_type`: `COMPANY`, `SECURITY`, `INDUSTRY`, `MARKET`, `COUNTRY`, `COMMODITY`
- `assessment_method`: `RULE`, `AI`, `HYBRID`, `HUMAN_REVIEW`
- `assessment_status`: `DRAFT`, `COMPLETED`, `REVIEW_REQUIRED`, `SUPERSEDED`, `REJECTED`
- `importance`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- `confidence`: `LOW`, `MEDIUM`, `HIGH`
- `time_horizon`: `IMMEDIATE`, `SHORT_TERM`, `MEDIUM_TERM`, `LONG_TERM`, `UNSPECIFIED`
- `impact_direction`: `POSITIVE`, `NEGATIVE`, `NEUTRAL`, `MIXED`, `UNKNOWN`
- `user_status`: `ACTIVE`, `LOCKED`, `DELETED`
- `credential_status`: `ACTIVE`, `LOCKED`, `REVOKED`
- `recovery_purpose`: `ACCOUNT_RECOVERY`
- `session_revoke_reason`: `LOGOUT`, `EXPIRED`, `ROTATED`, `PASSWORD_RESET`, `SECURITY`, `ADMIN`
- `ai_execution_status`: `RUNNING`, `SUCCEEDED`, `FAILED`, `CACHE_HIT`
- `alert_status`: `CANDIDATE`, `PENDING`, `SENT`, `FAILED`, `SUPPRESSED`
- `briefing_status`: `BUILDING`, `READY`, `DELIVERED`, `FAILED`

## 6. 삭제와 보존

- `source`, `evidence`, `event`, `entity`, `assessment`, `impact`는 공개 시장 기록과 감사 가능성을 위해 기본 `RESTRICT` 및 상태 변경을 사용한다.
- 계정 탈퇴 시 인증정보, 복구 이메일, reset·verification token, session, UserInterest, Alert와 Briefing은 정책에 따라 삭제한다.
- 만료·폐기 session과 사용·만료 token은 인증에 즉시 사용할 수 없게 하고, 짧은 보안 감사기간 이후 삭제한다.
- 정확한 보존기간은 실제 운영 전 개인정보·보안 정책에서 확정한다.
- `ai_execution`은 User FK가 없으며 비용·장애 분석에 필요한 기간만 보존한다.
- 원문 전체, 인증 token 원문, Secret과 불필요한 개인정보는 보존 대상이 아니다.

## 7. 명시적 비수집 데이터

v1은 다음을 저장하지 않는다.

- 실명, 성별, 생년월일, 주소, 직업, 전화번호
- Wi-Fi SSID/BSSID, Wi-Fi MAC, Wi-Fi 접속 이력
- Bluetooth MAC과 주변 Bluetooth 기기 목록
- 사용자 이동 경로와 과거 접속 장소
- 영구적인 물리 기기 식별자
- 평문 비밀번호
- session, reset 및 이메일 검증 token 원문
- API Key, DB Password와 외부 서비스 Secret
- AI 로그 내 인증정보와 불필요한 민감정보
- Event 수준의 positive/negative, 호재·악재 또는 매수·매도 값
- AI 생성 문장을 Evidence로 가장한 데이터

## 8. FUTURE

- Google, Apple 및 기타 OAuth/OIDC 인증
- MFA, Passkey와 복수 복구 수단
- JWT access/refresh 구조
- Entity subtype 테이블
- Event 병합·분리 전용 감사 테이블
- Impact별 직접 Evidence 연결 테이블
- AI provider/model/prompt 마스터 및 가격표·환율 이력
- 채널별 Alert 전달 시도와 공급자 모델
- 조직·팀·멀티테넌시 및 세분화된 권한
- 고도화된 개인화, 가상투자, 교육 기능
- 경제지표, 정책, 지정학, 산업과 원자재의 세부 도메인 모델
- Vector DB, Feature Store 및 전체 원문 보관 시스템

FUTURE 항목은 별도 요구와 Architecture 검토 없이 선행 구현하지 않는다.

## 9. Freeze 규칙

이 문서는 AIRA ERD v1의 관계와 책임 경계를 freeze한다. 다음 변경은 새 ADR 또는 명시적인 Architecture 검토가 필요하다.

- Event, Evidence, Assessment와 Impact의 책임 또는 관계 변경
- 개인정보 수집 확대
- nickname을 변경 가능하게 하거나 복구 이메일을 다른 목적으로 사용하는 변경
- 인증·session·reset token 원문 저장
- 외부 세션 식별자와 내부 user ID 결합
- AIExecution을 사용자별 공통 분석 반복 구조로 변경
- provider 종속 컬럼 또는 테이블을 핵심 도메인에 추가
- Event에 단일 호재·악재 또는 투자 지시 저장

Argon2id 실제 파라미터, 암호화 키 운용, token 난수 길이, token 보존기간과 cookie/CSRF 세부 설정은 배포 환경 보안 설계에서 확정한다. 이는 본 ERD의 관계 구조를 변경하지 않는 운영 보안 파라미터다.
