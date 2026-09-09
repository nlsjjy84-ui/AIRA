# ADR-008: Official Data Source Expansion

- Status: Accepted
- 기준일: 2026-09-09
- 상위 기준: [AIRA Architecture v1](../architecture/AIRA_ARCHITECTURE_V1.md)
- 관련 Registry: [AIRA Source Registry v1.0](../architecture/AIRA_SOURCE_REGISTRY_V1.md)
- 승인 근거: 사용자의 공식 Source 확장 결정 및 Architecture §12 변경관리 절차
- 대체 범위: Architecture §3.2의 FUTURE 적용을 아래 승인 범위에 한해 변경한다. 나머지 FUTURE 범위와 기존 ADR은 유지한다.

## Context

AIRA 핵심 정보 엔진 구현과 기준 명세 v1.0 전체 코드 감사가 완료되어 모든 책임이 ACCEPTED 상태다. 기존 Architecture §12는 외부 서비스 종속성 증가에 ADR 또는 이에 준하는 명시적 승인을 요구한다.

경제지표·정책·지정학·산업·원자재는 Architecture §3.2에서 FUTURE로 분류되었고, 핵심 정보 엔진보다 먼저 구현하지 않는다는 제한이 있었다. 이제 핵심 엔진이 완료되어 OpenDART 확장, KRX 및 제한된 한국은행 ECOS의 공식 Source 확장을 검토하고 승인한다. 기존 Architecture 원문을 덮어쓰지 않고 이 ADR에 변경 범위와 근거를 남긴다.

목적은 데이터 양이나 공급자 수를 늘리는 것이 아니라, 각 사실 유형을 담당하는 가장 권위 있는 1차 공식 Source에서 근거를 가져오는 것이다. API key 개수 자체가 정확성을 의미하지 않는다. 명확한 identity, provenance, 단위·기간·revision 검증이 필요하다.

## Decision

| Source | source type | 책임 | 승인 범위 |
|---|---|---|---|
| OpenDART | `REGULATOR` | 기업 공시, 공식 재무 Fact, 기업 Event의 1차 공식 Evidence | 현재 사용 중인 경로 유지 및 v1 확장 |
| KRX | `EXCHANGE` | 상장 증권 identity, 일별 가격·거래량·거래대금·시가총액, KOSPI/KOSDAQ 지수 | Market Fact의 1차 공식 Source |
| ECOS | `GOVERNMENT` | 한국은행 공식 통계, Macro Fact의 1차 공식 Source | 기준금리, USD/KRW, CPI 총지수, 실질 GDP만 |

범위 확장의 핵심 원칙은 `Source → Evidence → Fact/Event → Assessment`다. 이는 책임의 흐름이며 모든 Fact에 Event 또는 Assessment를 반드시 생성하라는 뜻이 아니다. 공식 데이터도 Assessment가 아니며 Source 단계에서 투자 의미를 부여하지 않는다.

KRX의 가격 상승·하락 자체를 positive/negative Event로 변환하지 않는다. ECOS의 거시지표 변화 자체를 호재·악재 또는 투자 Event로 자동 변환하지 않는다. 사실과 해석을 분리하는 ADR-001/002와 공통 분석 재사용·불필요한 AI 호출 방지 원칙은 유지한다.

## Scope Boundary

이번 승인에 포함한다.

- OpenDART 공시 discovery 및 original Evidence 확대
- OpenDART 선별 주요사항보고서: 증자·감자, 합병·분할·분할합병, CB/BW/EB, 자기주식, 영업정지·부도·회생절차, 주요 자산·타법인 지분 양수도, 주요 소송
- 기존 Annual CFS 강화
- KRX 6개 서비스: 유가증권/코스닥 종목기본정보, 유가증권/코스닥 일별매매정보, KOSPI/KOSDAQ 시리즈 일별시세정보
- ECOS 4개 Macro series: 기준금리, USD/KRW 공식 환율 계열, CPI 총지수, 실질 GDP

이번 승인에 포함하지 않는다.

- 실시간 시세 및 호가/체결 스트림
- ETF/ETN/ELW, 선물/옵션, 레버리지/인버스
- 자동매매, 투자자별 수급, 가격예측, 매수/매도 추천
- 가격 상승/하락 자동 Event 생성, 거시지표의 자동 호재/악재 판정
- 정책/지정학/산업/원자재 전체 범위 확장

승인은 위 Source 책임과 범위의 Architecture 결정이다. 각 API의 사용 가능 상태, 실제 contract, 이용조건 및 구현 gate가 통과되었다는 뜻은 아니다.

## Data Model Decision

- Provider-specific table/column을 핵심 ERD에 추가하지 않는다.
- 기존 `Source`, `Evidence`, `Entity`, `entity_external_identifier`, `Fact`, `Event`, `Assessment` 책임을 우선 재사용한다. Provider 필드와 코드 해석은 외부 연동 경계에 둔다.
- 회사명/종목명/지표명 문자열 추론이나 fuzzy matching으로 Entity를 연결하지 않는다. [ADR-006](ADR-006-entity-external-identifier-registry.md)의 explicit external identifier mapping을 따른다.
- Source의 provenance identity와 Entity의 외부 identifier는 별개다. 동일 mapping은 재사용하고 다른 Entity로의 자동 remapping은 conflict로 거부한다.
- ADR-006에서 별도 설계로 남긴 COMPANY–SECURITY 관계와 Security 식별자를 이번 ADR이 암묵적으로 확정하지 않는다. 지수·거시 series의 대상 Entity, 차원, 단위 및 정확한 기간 표현도 구현 gate에서 검토한다.
- 기존 Fact 구조가 KRX/ECOS의 instrument/series identity, 단위, 기간, revision을 무손실로 표현하지 못하면 억지로 COMPANY 재무 Fact에 넣지 않는다. Migration을 임의 생성하지 않고 별도 Architecture/Migration 검토를 먼저 수행한다.
- [ADR-007](ADR-007-company-financial-facts-public-read.md)의 exact-period Company financial public GET 계약을 유지한다. KRX/ECOS에 기존 endpoint나 public 노출 정책을 자동 확대하지 않는다.
- 이 ADR 자체는 schema/migration 변경 승인이 아니다. V1~V10 migration과 기존 Architecture/ERD/ADR-001~007을 수정하지 않는다.

## Alternatives Considered

1. **OpenDART 하나만 계속 사용**: 공시·기업 재무에는 적합하지만 거래소 Market Fact와 중앙은행 통계 책임까지 대신하지 못하므로 이번 제한 확장의 대안으로 거부했다.
2. **민간 종합 금융 API 하나 사용**: 편의성은 있으나 각 사실의 공식 1차 Source와 provenance를 분리·검증하려는 목적에 비해 중간 공급자 의존을 추가하므로 선택하지 않았다.
3. **KRX/ECOS 데이터를 모두 Event로 저장**: 관측값과 사건을 혼합하고 가격/지표 변화에 투자 의미를 부여할 위험이 있어 거부했다.
4. **Provider별 전용 DB table 생성**: 외부 contract가 핵심 모델로 누출되고 책임·revision 정책이 분산된다. 필요성이 입증되지 않은 schema 확장이므로 거부했다.
5. **회사명/지표명 문자열 기반 자동 mapping**: 동명이름, 명칭 변경, 표기 차이, 통계 개편에서 identity를 보장하지 못하고 ADR-006과 충돌하므로 거부했다.

## Consequences

공식 1차 Source의 책임이 명확해지고 provenance가 강화된다. 공급자별 데이터 의미를 분리한 상태로 Assessment의 근거 범위를 확장할 수 있다.

대신 외부 서비스 종속성, rate limit 대응, provider schema 변경 감시, KRX 이용조건·갱신 관리, ECOS 통계 개편·revision 관리 및 ingestion/revision 정책의 복잡도가 증가한다. 같은 observation의 재시도는 멱등적이어야 하며 기존 Historical Exact와 COMPLETED Assessment의 Evidence 집합을 보존한다.

운영 전, 특히 public/commercial 배포 전에는 각 Source의 이용조건을 다시 검토해야 한다. KRX의 현재 개인 비상업적 개발/포트폴리오 목적과 활용 신청이 공개·상업 배포 허가를 대신하지 않는다. API key 발급 또는 신청 완료와 실제 서비스 승인·사용 가능 상태를 구분한다.

이 문서는 개인정보 수집 확대, 보안 정책 완화 또는 AI 호출의 구조적 증가를 승인하지 않는다. API key 값은 읽거나 문서·DB·로그·Git에 기록하지 않는다. 실제 구현은 Registry의 implementation gates 통과 후 별도 작업으로 진행한다.
