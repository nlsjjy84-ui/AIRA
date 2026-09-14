# ADR-013: ECOS REAL_GDP Quarterly Fact Ingestion

- Status: Accepted
- 기준일: 2026-09-13
- 상위 기준: AIRA 기준 명세 v1.0, ADR-009~012
- 적용 Packet: ECOS-31

## Context

ECOS-1~29에서 official source, REAL_GDP contract, bounded observation read,
Evidence snapshot/reuse, canonical Korea entity, REAL_GDP series binding을 닫았다.
ECOS-30에서 provider-neutral REAL_GDP Fact, `KRW_BILLION`, statistical context와
provider-neutral statistical dedup identity를 닫았다.

ECOS-31은 validated quarterly observation을 기존 Fact provenance 구조에 연결하는
최소 persistence 책임만 가진다. 새 provider semantics나 macro Event는 만들지 않는다.

## Decision

입력은 validated `EcosRealGdpObservationResult`와 collection time이다.
각 observation의 `TIME=YYYYQn`은 정확한 calendar quarter start/end로 변환한다.
숫자 `DATA_VALUE`가 없는 observation은 UNKNOWN으로 추론하거나 건너뛰지 않고 BLOCK한다.

한 ingestion transaction 안에서 다음 순서를 사용한다.

1. 모든 observation을 persistence 전에 숫자/quarter ingestibility로 확인한다.
2. 기존 REAL_GDP series binding을 register-or-reuse 한다.
3. 기존 ECOS Evidence snapshot 계약으로 Evidence를 register-or-reuse 한다.
4. provider-neutral `StatisticalFactDedupKey`로 quarter Fact를 find-or-create 한다.
5. `fact_statistical_context`를 REAL_GDP series + `KRW_BILLION`으로 register-or-reuse 한다.
6. 기존 `FactAssertion`에 Evidence와 exact `TIME` locator를 연결한다.

## Idempotency and Conflict

동일 Evidence identity + 동일 snapshot은 기존 Evidence를 재사용한다.
동일 Fact dedup identity는 Fact를 새로 만들지 않는다.
동일 `(fact_id, evidence_id)` assertion은 재사용한다.
따라서 동일 ingestion 재실행은 row duplication 없이 같은 identifiers를 반환한다.

겹치는 다른 Evidence가 같은 quarter에 같은 숫자를 주장하면 Fact는 SUPPORTED를 유지하고
새 assertion만 추가한다. 다른 숫자를 주장하면 기존 Fact 값을 overwrite하지 않고
기존 Fact conflict semantics에 따라 `CONFLICTING`으로 전환하며 각 Evidence assertion 값을 보존한다.

같은 request identity의 Evidence content drift는 기존 Evidence 계약대로 BLOCK한다.
provider revision number를 추론하거나 자동 증가시키지 않으며 Fact supersession도 만들지 않는다.

## Boundary

- 새 observation table 없음
- 새 migration 없음
- Event / Assessment 생성 없음
- OpenDART earnings normalization 변경 없음
- `FactPeriodEvidence` 추가 없음: ECOS value Evidence의 row locator가 `TIME`을 직접 보존한다.
- revision/supersession 자동화 없음

ECOS provider codes와 unit strings는 Evidence/source mapping 경계에 남고,
Fact identity에는 canonical country, metric/frequency/adjustment/value-kind/unit와 exact period만 들어간다.
