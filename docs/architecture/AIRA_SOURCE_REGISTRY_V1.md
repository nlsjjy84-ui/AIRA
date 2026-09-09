# AIRA Source Registry v1.0

- Version: 1.0
- Status: Approved
- 기준일: 2026-09-09
- 상위 기준: [Architecture v1](AIRA_ARCHITECTURE_V1.md), [ERD v1](AIRA_ERD_V1.md)
- 관련 ADR: [ADR-001](../adr/ADR-001-event-centered-model.md), [ADR-002](../adr/ADR-002-evidence-assessment-separation.md), [ADR-006](../adr/ADR-006-entity-external-identifier-registry.md), [ADR-007](../adr/ADR-007-company-financial-facts-public-read.md), [ADR-008](../adr/ADR-008-official-data-source-expansion.md)

이 문서는 DB의 `source` 테이블을 대체하지 않는다. 공식 데이터 Source의 책임, identity, provenance, revision, 시간 의미, 보안 및 범위 정책을 정의하는 Architecture Registry다. Source Registry의 provenance 책임과 Entity 외부 식별자 Registry의 identity 책임을 구분한다.

Architecture §3.2의 FUTURE 제한은 ADR-008의 명시적 승인 범위에 한해서 변경된다. 기존 Architecture/ERD/ADR 원문과 기존 public API 계약은 유지한다. 문서 승인과 API contract 검증·이용조건 승인·schema 변경 승인은 서로 다르다.

## A. Registry 공통 원칙

1. 공식 Source를 많이 붙이거나 데이터 양을 늘리는 것이 목적이 아니다.
2. 사실 유형별 가장 권위 있는 1차 공식 Source를 사용한다.
3. 외부 provider 형식을 핵심 domain model로 누출하지 않는다.
4. Source 데이터와 AIRA Assessment를 분리한다. `Source → Evidence → Fact/Event → Assessment`는 모든 Fact의 Event/Assessment 생성을 강제하지 않는다.
5. 회사명/종목명/지표명 fuzzy matching, substring 또는 문자열 추론으로 identity를 연결하지 않는다.
6. Official external identifier와 ADR-006의 명시적 mapping을 사용한다. `(namespace, identifier_type, identifier_value)`의 기존 Entity 연결을 자동 remapping하지 않는다.
7. 동일 observation 재처리는 멱등적이어야 한다.
8. 기존 Historical Exact 의미를 파괴하지 않는다.
9. 기존 COMPLETED Assessment의 Evidence 집합을 사후 확장하지 않는다.
10. Secret/API key를 Source/Evidence/DB/log/Git에 저장하지 않는다. AI 입력 및 테스트 산출물에도 넣지 않는다.

## B. Registry Source row

아래는 논리적인 registry 값이다. 신규 컬럼, provider 전용 테이블 또는 seed migration을 요구하지 않는다. 기존 Source와의 재사용·충돌 확인은 구현 시 수행한다.

| Registry key | source_type | name | canonical domain |
|---|---|---|---|
| `OPENDART` | `REGULATOR` | Financial Supervisory Service OpenDART | `opendart.fss.or.kr` |
| `KRX_OPEN_API` | `EXCHANGE` | Korea Exchange Open API | `openapi.krx.co.kr` |
| `BOK_ECOS` | `GOVERNMENT` | Bank of Korea ECOS | `ecos.bok.or.kr` |

실제 source UUID는 환경별 데이터이므로 문서에 고정하지 않는다. Canonical domain은 Source의 대표 domain이며 모든 Evidence original URL의 host가 같아야 한다는 뜻은 아니다. 공식 공시 원문과 통계의 제공기관·원작성기관 metadata를 보존한다.

## C. OpenDART

### 책임과 identity

- Company identity metadata, 공시 discovery, 공시 원문 Evidence
- Annual CFS financial Fact 및 선별 material disclosure Event
- `CORP_CODE`: leading zero를 보존하는 8자리 문자열
- `rcept_no`: 공시 접수 identity
- XBRL `account_id` 및 exact reporting period

ADR-006에 따라 `CORP_CODE → COMPANY Entity`를 explicit mapping한다. 회사명으로 추론하지 않는다. 종목 identity와 회사 identity를 같은 것으로 취급하지 않는다.

### Annual CFS

- CFS 우선이며 OFS 등을 조용히 대체해 같은 의미로 저장하지 않는다.
- 공식 `account_id` exact mapping만 허용한다. `account_nm` 추론/substr/fuzzy mapping은 금지한다.
- `ifrs-full_Revenue → REVENUE` exact alias를 허용하고 기존 `ifrs_Revenue` alias를 유지한다.
- 동일 filing에서 동일 metric으로 여러 exact alias가 충돌하면 임의로 하나를 고르거나 합산하지 않고 명시적 conflict로 거부한다.
- 회사별 공식 결산월 `acc_mt`와 해당 fiscal year에 근거한 exact fiscal period를 사용한다. 비12월 결산을 포함하며 `acc_mt`가 없거나 invalid면 추측하지 않는다. 결산기 변경 등으로 기간이 결정되지 않으면 추가 공식 근거 확인 전 수집을 보류한다.
- Event 제목의 회계연도는 reporting period의 fiscal year에 근거한다. `occurredAt.year` 또는 수집연도로 대체하지 않는다.

### Evidence 및 정정공시

`rcept_no` 기반 original disclosure를 추적한다. 구조화 API만 사용하더라도 원문 provenance를 잃지 않으며 가능하면 `rcept_no → original disclosure`를 연결한다. 확인하지 않은 원문 연결을 확인된 것처럼 표시하지 않는다.

정정공시의 새 `rcept_no`는 새로운 Evidence identity로 수집한다. Report title 문자열만으로 correction lineage를 추론하지 않는다. 공식적으로 결정 가능한 lineage가 있을 때만 연결하고, 결정되지 않으면 별도 identity로 보존한다. 기존 Historical Evidence/Assessment를 overwrite하지 않는다. Lineage 저장이 기존 모델에 맞지 않으면 별도 검토하며 임의 컬럼을 추가하지 않는다.

### 선별 주요사항 Event v1

- 유상/무상증자, 감자
- 합병/분할/분할합병
- CB/BW/EB
- 자기주식 취득/처분/신탁
- 영업정지, 부도, 회생절차
- 주요 자산 또는 타법인 주식/출자증권 양수·양도
- 주요 소송

전체 36종을 무조건 구현하지 않는다. 위 유형의 실제 endpoint subset, 공식 identity, 중복/정정 의미 및 기존 Event 유형과의 mapping을 구현 전에 확정한다. 제목의 단어만으로 사건 유형이나 투자 방향을 판단하지 않는다.

## D. KRX

### 신청 상태와 범위

사용자 제공 상태: API key 발급 완료, 다음 6개 API 활용 신청 완료. 신청 목적은 개인 연구, 기간은 `12M`이다. 이는 이번 작업에서 실제 승인·사용 가능 상태를 조회했다는 뜻이 아니다.

1. 유가증권 종목기본정보
2. 코스닥 종목기본정보
3. 유가증권 일별매매정보
4. 코스닥 일별매매정보
5. KOSPI 시리즈 일별시세정보
6. KOSDAQ 시리즈 일별시세정보

현재 범위는 개인 비상업적 개발/포트폴리오다. 공개/상업 배포 전 KRX 이용조건을 반드시 재검토하고 신청 갱신을 관리한다. 서비스별 endpoint code, 승인 상태, 호출 한도는 이 문서에서 확정하지 않는다.

### Identity와 observation

- 종목기본정보의 공식 identifier를 사용하고 ISIN/표준코드와 단축코드의 의미를 분리한다.
- Endpoint별 `ISU_CD` 의미가 다를 수 있으므로 필드명만 보고 전역 ID로 간주하지 않는다.
- Market + official identifier의 의미를 확인하여 ADR-006의 namespace/type/value로 명시적으로 mapping한다. 회사명/종목명 연결은 금지한다.
- ADR-006이 별도 설계로 남긴 COMPANY–SECURITY 관계를 이름이나 종목 코드의 유사성으로 채우지 않는다.
- Observation identity의 구성 원칙은 market, official instrument identifier, `BAS_DD`/trading date, API service contract다. 실제 serialization과 범위는 endpoint contract 검증 후 확정한다.
- 지수 identity를 `IDX_NM` 문자열만으로 영구 canonical identity로 만들지 않는다. 실제 API contract에서 공식 identifier 존재·유일성·지속성을 확인한다. 없으면 이름을 대신 쓰지 않고 명시적 identity 설계 검토를 먼저 수행한다.

### Provider-neutral Fact 후보

| 대상 | Predicate 후보 |
|---|---|
| Market Fact | `OPEN_PRICE`, `HIGH_PRICE`, `LOW_PRICE`, `CLOSE_PRICE`, `TRADING_VOLUME`, `TRADING_VALUE`, `MARKET_CAP`, `LISTED_SHARES` |
| Index Fact | `INDEX_OPEN`, `INDEX_HIGH`, `INDEX_LOW`, `INDEX_CLOSE`, `INDEX_VOLUME`, `INDEX_TRADING_VALUE`, `INDEX_MARKET_CAP` |

후보 명칭은 기존 Fact enum/schema에 추가되었다는 뜻이 아니다. 실제 응답에 없는 필드를 추정하지 않으며 단위·통화·거래일·시장·대상 Entity와 가격 조정 여부 등 공식 의미를 검토한다. 기존 Fact 모델에 무손실로 맞지 않으면 Architecture 검토가 선행한다.

가격 상승/하락 자체는 Event가 아니며 positive/negative를 Event 의미로 저장하지 않는다. 전일대비/등락률은 원 Evidence에 보존할 수 있다. AIRA 계산값이 필요하면 원 Fact와 명시적 계산식으로 결정론적으로 계산하고 외부 원 Fact와 구분한다.

## E. ECOS

사용자 제공 상태: API key 발급 완료. v1 승인 범위는 한국은행 기준금리, USD/KRW 공식 환율 계열, CPI 총지수, 실질 GDP뿐이다.

### Verification targets — binding constants 아님

| Series | STAT_CODE 후보 | ITEM_CODE 후보 | 상태 | Predicate 후보 |
|---|---|---|---|---|
| 한국은행 기준금리 | `722Y001` | `0101000` | `VERIFY_BEFORE_IMPLEMENTATION` | `BASE_RATE` |
| USD/KRW 공식 환율 계열 | `731Y001` | `0000001` | `VERIFY_BEFORE_IMPLEMENTATION` | `USD_KRW_RATE` |
| CPI 총지수 | `901Y009` | `0` | `VERIFY_BEFORE_IMPLEMENTATION` | `CPI_INDEX` |
| 실질 GDP | `200Y104` | `1400` | `VERIFY_BEFORE_IMPLEMENTATION` | `REAL_GDP` |

위 코드는 조사 후보이며 VERIFIED 또는 확정된 binding constant가 아니다. `CYCLE`과 `UNIT_NAME`도 미확정이다. 실제 구현 전 공식 ECOS `StatisticItemList`/metadata 응답으로 `STAT_CODE`, 필요한 전체 `ITEM_CODE` 차원, `CYCLE`, `UNIT_NAME`을 재검증한 뒤 binding한다.

사용자 API key는 채팅/보고서/소스에 노출하지 않고 로컬 실행환경에서만 공식 metadata 조회에 사용한다. 이번 문서 작업에서는 key를 읽거나 API 요청을 하지 않는다. 검증 결과는 key가 제거된 metadata와 검증 시각·contract 근거로 기록한다.

계열의 단위, 주기, 통계 기준연도, 계절조정 여부, 환율 방향·단위 및 데이터 개편 의미가 요청한 series와 일치해야 한다. 같은 지표명만으로 서로 다른 계열을 합치지 않는다. 코드 조합의 공식 identity를 검증하고 대상 Entity와의 explicit mapping 계획을 확정한다.

Macro predicate는 provider-neutral 후보일 뿐이다. 현재 Fact domain에 실제로 맞는지, 통화 없는 수치·비율·지수·기간 및 대상 Entity를 무손실로 표현하는지 구현 전 재검토한다. 안 되면 별도 Architecture decision 후에만 migration 여부를 검토한다.

GDP 전기비, CPI 전년동월비, 환율 변화율 등은 이 Registry의 원 Fact에서 구분되는 파생값이다. 공식 제공 파생계열을 사용하더라도 원 수준값과 같은 predicate로 취급하지 않는다. AIRA 계산 시 원 Fact identity, 비교 기간, 계산식 및 반올림 정책을 명시한다.

## F. 시간 의미

| 시간 | 의미 |
|---|---|
| reporting period | 기업 재무 Fact의 정확한 회계기간/fiscal year |
| trading date | 거래소 관측값이 속하는 거래일 |
| statistical `TIME` | 통계 관측 대상 기간 또는 시점; 주기 metadata와 함께 해석 |
| `occurredAt` | 공식 근거로 확인 가능한 실제 사건 발생시각 |
| `publishedAt` | Source가 제공하는 해당 Evidence의 발행/공개 시각 |
| `collectedAt` | AIRA가 해당 Evidence를 수집한 시각 |

- Reporting period end를 `occurredAt`으로 사용하지 않는다.
- Trading date를 Event `occurredAt`으로 자동 변환하지 않는다.
- Statistical `TIME`을 `publishedAt`으로 대체하지 않는다.
- `collectedAt`을 `publishedAt` 또는 `occurredAt`의 대체값으로 쓰지 않는다.
- Source가 `publishedAt`을 제공하지 않으면 null을 허용한다.
- 신뢰 가능한 실제 사건 발생시각이 없으면 ERD가 허용하는 `occurredAt = null`을 사용한다. 날짜만 있는 응답에 임의 자정·시간대를 붙여 정확한 발생시각인 것처럼 만들지 않는다.

## G. Evidence / Revision

- Source 범위의 same external identity + same revision에서 content 또는 provenance가 충돌하면 명시적으로 reject한다. 수집시각만 바뀐 재시도를 새 내용으로 취급하지 않는다.
- 새로운 공식 revision은 새 Evidence revision 또는 새 immutable Evidence identity로 보존한다. 선택은 provider의 identity semantics와 검증된 contract에 따른다.
- 기존 Evidence를 overwrite하지 않는다. 기존 COMPLETED Assessment의 `evidenceIds`를 새 revision 때문에 확장하지 않는다.
- 새 revision이 새로운 판단을 요구하면 새 Assessment를 생성한다. Historical Exact는 당시 Evidence/Assessment를 유지한다.
- Current는 explicit supersession 정책으로 결정하며 단순 latest timestamp로 고르지 않는다.
- KRX/ECOS의 동일 observation identity도 공식 값이 나중에 수정될 수 있다. 재수집 값이 달라졌다고 조용히 overwrite하지 않는다.
- Content hash와 provenance를 함께 검증한다. Hash 일치만으로 출처를 무시하거나 서로 다른 observation을 합치지 않는다.
- Provider가 revision 식별자를 제공하지 않는 경우에도 값 차이만으로 임의의 공식 revision을 주장하지 않는다. 원 observation과 변경 관측을 검증 가능한 방식으로 보존하는 정책, 동시 재처리의 revision 할당 및 충돌 처리 방법을 구현 gate에서 먼저 확정한다.

## H. Secret / Logging

환경변수 이름만 정의한다. 실제 값은 기록하지 않는다.

| Source | 환경변수 이름 | 금지/마스킹 대상 |
|---|---|---|
| OpenDART | `OPENDART_API_KEY` | `crtfc_key` query value logging 금지/마스킹 |
| KRX | `KRX_API_KEY` | `AUTH_KEY` header logging 금지 |
| ECOS | `ECOS_API_KEY` | key가 URL path에 포함될 수 있으므로 raw request URL logging 금지 |

ECOS 등 credential-bearing 요청은 sanitized URL/locator만 저장한다. 원문 provenance는 비밀정보 없는 공식 식별자·문서 위치로 재현 가능해야 한다. Secret을 제거하기 전 raw URL/header를 예외 메시지, stacktrace, report, test artifact, Source/Evidence 또는 AI 입력으로 전달하지 않는다. 키 회전 시 데이터 identity나 provenance hash에 key 값이 영향을 주어서는 안 된다.

## I. Failure / Rate / Retry

- Provider outage와 internal processing failure를 구분한다.
- 실패가 기존 valid Fact/Evidence를 손상시키지 않아야 한다. 부분 처리의 rollback/재개 경계를 구현 전에 정의한다.
- Retries는 idempotent이며 exponential backoff/jitter를 후보로 검토한다.
- Key rotation 또는 multiple key를 rate-limit 우회 수단으로 사용하지 않는다.
- Incremental ingestion을 우선하고 이미 처리된 동일 observation은 skip/cache한다. 공식 revision 탐지에 필요한 재검증까지 영구 생략하는 cache는 허용하지 않는다.
- Rate limit은 provider 공식 정책을 확인하여 implementation config로 관리한다. 숫자를 코드에 영구 하드코딩하기 전에 공식 정책을 확인한다.
- 재시도 횟수·대기시간·한도 및 KRX 신청 기간/갱신 상태는 운영 contract 확인 후 정한다. 이 문서에 미검증 호출량을 확정값으로 기록하지 않는다.

## J. Implementation Gates

아래 gate는 현재 `PENDING`이다. 이 Registry의 Approved 상태와 혼동하지 않는다. 해당 Source의 구현 코드를 시작하기 전에 관련 gate가 모두 PASS되어야 한다.

| Source | PASS에 필요한 근거 |
|---|---|
| OpenDART | 현재 adapter와 Registry 정책의 gap audit; discovery/original Evidence identity 확정; material Event endpoint subset 확정; exact alias 충돌·결산월/기간·정정 identity 정책 확인 |
| KRX | 6개 API 승인/사용 가능 상태 확인; secret 제거된 실제 response schema 캡처; endpoint별 identifier semantics 검증; instrument/index 및 COMPANY–SECURITY를 포함한 Entity mapping 계획; license/use condition 확인 |
| ECOS | 공식 metadata API로 4개 series의 code/cycle/unit 및 필요한 차원 재검증; 정확한 계열 의미 확인; Macro Fact가 기존 Fact model에 무손실로 들어가는지 검토; 불가능하면 migration 전에 Architecture decision |
| 공통 | provider-specific core schema leakage 없음; fuzzy identity mapping 없음; secret in Git 없음; observation/revision/시간·단위 contract 확정; tests planned before implementation |

테스트 계획에는 다음을 포함한다.

- Official identifier의 leading zero, namespace 충돌, 잘못된 Entity remapping 거부
- Exact account alias 충돌, 비12월 결산, 결산월 누락/invalid, 원문 provenance 추적
- 동일 observation 재처리·동시 ingestion 멱등성 및 same revision content/provenance conflict
- 새 Evidence revision과 기존 COMPLETED Assessment Evidence 집합/Historical Exact 보존
- Reporting period/trading date/statistical TIME과 발생·발행·수집 시각 분리, null 처리
- Provider outage, 부분 실패 rollback, retry/rate 제약, URL/header/예외/산출물의 secret 비노출
- ADR-007 exact-period public read 및 기존 Current/Historical/Alert/Briefing 회귀 영향

기존 Source/Evidence/Entity/외부 identifier/Fact/Event/Assessment 책임을 우선 재사용한다. 지수·거시 series를 표현하기 위해 provider별 컬럼을 추가하거나 임의 Entity 유형을 만들어서는 안 된다. 기존 모델로 안전하게 표현할 수 없는 경우 별도 Architecture/Migration 검토를 먼저 수행한다. 이 문서는 schema/migration, 새로운 public endpoint 또는 API 코드 연동을 승인하지 않는다.
