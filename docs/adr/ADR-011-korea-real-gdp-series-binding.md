# ADR-011: Korea REAL_GDP Series Binding

- Status: Accepted
- 기준일: 2026-09-12
- 상위 기준: AIRA 기준 명세 v1.0, ADR-008, ADR-009, ADR-010
- 적용 Packet: ECOS-29

## Context

ECOS-27은 provider-neutral statistical series 구조를 만들었고,
ECOS-28은 대한민국 COUNTRY canonical identity를 `COUNTRY:KR`로 고정했다.
이제 두 구조를 BOK ECOS의 검증된 REAL_GDP metadata와 하나의 결정론적 등록 흐름으로 연결해야 한다.

## Decision

대한민국 실질 GDP series binding은 다음 하나의 계약으로 고정한다.

- subject: `COUNTRY:KR`
- metric: `REAL_GDP`
- frequency: `QUARTERLY`
- adjustment: `SEASONALLY_ADJUSTED`
- value kind: `LEVEL`
- Source: `BOK_ECOS / GOVERNMENT / Bank of Korea ECOS / ecos.bok.or.kr`
- provider binding key: `StatisticSearch:200Y104:1400:-:-:-:Q`
- provider series name: ECOS `200Y104` 공식 STAT_NAME
- provider item name: ECOS `1400` 공식 ITEM_NAME
- provider frequency code: `Q`
- provider unit name: `십억원`
- metadata locator: `StatisticItemList/json/kr/200Y104/1/1000`

동일 계약의 반복 실행은 COUNTRY, Source, statistical series, source mapping의 기존 row를 재사용한다.
어느 단계든 기존 row가 고정 계약과 다르면 자동 보정하거나 overwrite하지 않고 BLOCK한다.
전체 등록 흐름은 하나의 transaction에서 실행되어 후속 mapping 충돌 시 앞 단계의 신규 write도 함께 rollback된다.

BOK_ECOS Source canonical 검증은 Evidence 등록과 Series Binding이 동일한 공통 계약을 사용한다.
Source key만 같고 name/domain/type/active가 다른 row를 정상 Source로 취급하지 않는다.

## Boundary

- 새 table 또는 migration을 추가하지 않는다.
- ECOS STAT/ITEM code를 COUNTRY canonical identity 또는 external identifier에 넣지 않는다.
- `Fact`, `FactPredicate`, `FactAssertion`, `FactPeriodEvidence`를 변경하지 않는다.
- 관측값 DATA_VALUE를 저장하지 않는다.
- ECOS network 호출을 수행하지 않는다.
- observation period, Fact unit/value semantics, revision/supersession은 후속 Packet에서 다룬다.
- 통계 변화로 Event 또는 positive/negative 해석을 만들지 않는다.

## Consequences

후속 observation ingestion은 COUNTRY, statistical series, Source/provider binding을 다시 추론할 필요가 없다.
ECOS REAL_GDP ingestion은 이 binding 결과의 식별자만 사용하며 provider metadata를 임의 재구성하지 않는다.
