# Missing Data Interpretation Boundary v1

## Core rule
Absence of provider data is not equivalent to a numeric zero, a false boolean, or proof that an event/fact does not exist.

## Global semantics
- `NO_DATA`, empty result, blank metric, unavailable period, or unsupported field means `not observed / not available under this query contract` unless the provider explicitly defines a stronger meaning.
- Missing numeric data must not become `0`.
- Missing Event data must not become `no event occurred`.
- Missing relationship data must not become `no relationship exists`.
- Missing Historical Exact witness must not become a guessed period.

## Provider examples
- KRX `-`/absent metric -> no Fact for that metric, not zero.
- OpenDART status `013` -> no data for that request contract, not proof that no material event/filing exists outside the request scope.
- ECOS blank/non-numeric required observation -> BLOCK ingestion, not UNKNOWN/zero.

## User-facing meaning
Use neutral states such as `자료 없음`, `확인되지 않음`, `이 범위에서 조회되지 않음`, or `검증 불가` according to the actual contract. Do not overstate absence.

## Implementation boundary
Null handling, DTO flags, query behavior, UI states, and tests are CODEX-FIRST/later work.