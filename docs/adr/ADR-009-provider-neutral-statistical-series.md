# ADR-009: Provider-neutral Statistical Series Foundation

- Status: Accepted
- 기준일: 2026-09-12
- 상위 기준: AIRA 기준 명세 v1.0, ADR-008
- 적용 Packet: ECOS-27

## Context

ADR-008은 제한된 ECOS Macro Fact 확장을 승인했지만 schema 변경은 별도 검토하도록 했다.
현재 `Fact`의 숫자 생성 경로는 기업 재무 의미와 3자리 통화코드를 요구하고,
`FactPredicate`도 `REVENUE`, `OPERATING_INCOME`만 지원한다.
따라서 ECOS `200Y104 / 1400 / Q` 실질 GDP를 기존 Company Fact에 강제로 넣으면
series identity, 단위와 통계 의미가 손실된다.

## Decision

통계 관측값을 Fact에 연결하기 전에 provider-neutral한 통계 series identity를 별도 보존한다.
ECOS-27은 additive V12로 다음 두 책임만 추가한다.

- `statistical_series`: AIRA 내부의 통계 series 의미 identity
- `statistical_series_source_mapping`: 공식 Source의 provider binding과 metadata mapping
`statistical_series` identity는
`subject_entity_id + metric + frequency + adjustment + value_kind`로 고정한다.
초기 승인 값은 `REAL_GDP / QUARTERLY / SEASONALLY_ADJUSTED / LEVEL`뿐이며,
새 metric/frequency 의미는 별도 migration과 검토 없이 추가하지 않는다.

`statistical_series_source_mapping`은 `source_id + provider_binding_key`를 provider binding identity로 사용한다.
동일 binding의 재등록은 기존 row를 재사용한다.
동일 identity에서 series 연결, provider series/item name, frequency code, unit name,
metadata locator가 달라지면 overwrite하지 않고 conflict로 거부한다.
Provider metadata 문자열은 surrounding whitespace를 자동 보정하지 않고 거부한다.

## Boundary

- Provider-specific table 또는 핵심 Entity 컬럼을 만들지 않는다.
- ECOS STAT/ITEM code를 COUNTRY Entity external identifier로 사용하지 않는다.
- COUNTRY canonical key 생성 규칙은 이번 ADR에서 정의하지 않는다.
- `Fact`, `FactPredicate`, `FactAssertion`, `FactPeriodEvidence`의 의미를 변경하지 않는다.
- `REAL_GDP`의 subject가 `COUNTRY`인지 여부는 registry service에서 강제한다. 이는 ADR-006의 provider-specific Entity type 검증과 같은 application-boundary 패턴이다.
- `fact_statistical_context`와 non-currency statistical Fact 생성은 후속 Packet으로 분리한다.
- 통계 관측 변화만으로 Event 또는 positive/negative 해석을 생성하지 않는다.
## Consequences

V12는 V1~V11을 수정하지 않는 additive migration이다.
기존 Company financial public read와 Event/Assessment 경로는 그대로 유지된다.
통계 series identity와 provider metadata가 먼저 고정되므로 후속 Fact 연결 시
provider code, 단위, frequency를 다시 추론할 필요가 없다.

반대로 V12만으로 ECOS observation Fact ingestion이 완성되는 것은 아니다.
COUNTRY subject bootstrap, statistical Fact value/unit semantics,
observation-period mapping과 revision/supersession은 각각 후속 implementation gate에서 닫아야 한다.
