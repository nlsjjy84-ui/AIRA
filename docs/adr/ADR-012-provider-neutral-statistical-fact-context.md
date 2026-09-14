# ADR-012: Provider-neutral Statistical Fact Context

- Status: Accepted
- 기준일: 2026-09-12
- 상위 기준: AIRA 기준 명세 v1.0, ADR-009, ADR-010, ADR-011
- 적용 Packet: ECOS-30

## Context

기존 `Fact`는 기업 재무 Fact를 중심으로 생성되며 `REVENUE`, `OPERATING_INCOME`
숫자 Fact는 3자리 `currency_code`를 요구한다. 반면 ECOS REAL_GDP 관측값은
대한민국 분기 실질 GDP 수준이며 공급자 단위는 `십억원`이다.

`statistical_series_source_mapping.provider_unit_name`은 공급자 metadata이므로
그 문자열을 AIRA Fact의 provider-neutral 단위 의미로 사용할 수 없다.
또한 GDP 숫자만 Fact에 저장하면 어느 statistical series 관측인지 손실된다.

## Decision

`Fact`는 그대로 공통 값/상태/Assertion 모델로 재사용하되 통계 Fact 생성 계약을 분리한다.
초기 허용 통계 predicate는 `REAL_GDP` 하나다.

REAL_GDP Fact는 다음을 강제한다.

- subject는 `COUNTRY`
- `value_type = NUMBER`
- `currency_code = NULL`
- `event_id = NULL`
- exact calendar quarter `period_start / period_end`
- 기존 `FactAssertion`을 통한 SUPPORTED provenance 규칙 유지

기업 재무 `supportedNumber` 경로는 `REVENUE / OPERATING_INCOME`에만 한정한다.
REAL_GDP는 별도 statistical-number factory를 사용한다.
## Statistical Context and Unit

V14 adds one provider-neutral 1:1 table:

- `fact_statistical_context.fact_id`
- `statistical_series_id`
- `canonical_unit`

Initial canonical unit is `KRW_BILLION`, meaning one stored unit equals KRW 1,000,000,000.
ECOS `UNIT_NAME=십억원` is already strict-validated by the provider parser; only that exact
provider contract may map 1:1 to `KRW_BILLION`. No name inference or arithmetic conversion is allowed.

A REAL_GDP Fact must have exactly one matching statistical context at transaction commit.
The linked series must have the same subject and metric `REAL_GDP`; the subject must be COUNTRY.
A non-statistical Fact must not have statistical context. Conflicting metadata is never overwritten.

Statistical Fact dedup identity is provider-neutral and includes subject canonical key,
metric, frequency, adjustment, value kind, canonical unit, period start and period end.
Provider source IDs, ECOS codes and collection time are excluded from the dedup identity.

## Boundary

- Existing earnings Fact/Event/Assessment semantics are unchanged.
- `FactAssertion` remains the value provenance mechanism.
- `FactPeriodEvidence` remains the exact-period evidence mechanism where applicable.
- V14 does not ingest ECOS observations into Fact rows.
- V14 does not create Events from macro movements.
- Revision/supersession and observation persistence are handled by a later Packet.

## Consequence

The same statistical Fact can later be supported by provider Evidence without embedding provider codes
or provider unit strings into the Fact identity. Company financial Facts keep their existing currency
semantics, while REAL_GDP can preserve a non-currency numeric value without loss of series or unit meaning.
