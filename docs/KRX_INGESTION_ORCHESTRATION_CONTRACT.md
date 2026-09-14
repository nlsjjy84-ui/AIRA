# KRX Ingestion Orchestration Contract

## Dataset boundary
Treat each `{apiId, basDd}` response as one independent provider dataset/snapshot.
The six approved endpoints are not one giant transaction. KOSPI stock, KOSDAQ stock, KOSPI index, and KOSDAQ index datasets may succeed/fail independently according to their own snapshot identity.

## Network vs DB transaction
For a stock market/date pair:
1. fetch base-info and daily-trading responses outside any DB transaction;
2. strictly validate transport/provider status and explicit requested `basDd` semantics;
3. resolve every daily row to exactly one base-info SECURITY identity by code;
4. build deterministic Evidence registrations + Market Fact inputs in memory;
5. only after validation succeeds, enter one DB transaction for that market/date persistence bundle.

No HTTP request may occur while holding the DB write transaction.
## Atomic stock persistence bundle
Inside the transaction:
- register/reuse the canonical KRX EXCHANGE Source;
- register/reuse base-info Evidence for `{baseApiId, basDd}`;
- register/reuse daily-trading Evidence for `{dailyApiId, basDd}`;
- create/reuse all resolved SECURITY entities + external identifiers;
- register/reuse all eight Market Facts per valid daily row;
- register/reuse FactAssertions to the shared daily-trading Evidence.

If an identity conflict, Evidence conflict, Fact conflict-handling bug, or persistence invariant fails unexpectedly, rollback that entire market/date persistence bundle.
Do not leave Evidence committed with an incomplete subset of SECURITY/Facts due to an implementation failure.
## Validation strictness
- Identity resolution is all-or-nothing for one stock dataset: if any daily row cannot resolve uniquely to base-info by the settled code join, do not persist a partial market/date Fact set.
- Required v1 Market Fact source fields must parse as non-negative numeric values; zero remains valid. A blank/malformed required field blocks that market/date Fact dataset rather than silently dropping one metric.
- Name mismatches are never used to repair code identity; names may be retained for diagnostics only.
- Response row order is irrelevant after canonicalization.

## Index Evidence-only datasets
For `kospi_dd_trd` and `kosdaq_dd_trd`:
- fetch/validate outside a DB transaction;
- persist only the KRX Source + Evidence snapshot in a short transaction;
- no Entity, Fact, Event, or Assessment registration occurs in v1;
- failure of an index snapshot does not rollback an already-closed stock dataset transaction.

## Re-run behavior
A complete re-run for the same date must be idempotent across Source, Evidence, SECURITY identifiers, Fact, and FactAssertion. Any same Evidence identity with different canonical content remains a hard BLOCK, not an update.
## Same-date identity resolution
For each stock market/date packet, identity resolution must use the base-info endpoint for the same explicit `basDd` as the daily-trade endpoint.
- KOSPI packet pairs `stk_isu_base_info(basDd)` with `stk_bydd_trd(basDd)`.
- KOSDAQ packet pairs `ksq_isu_base_info(basDd)` with `ksq_bydd_trd(basDd)`.
- Daily `ISU_CD` is resolved only through same-date base-info `ISU_SRT_CD`; the resulting base-info `ISU_CD` is the standard-code external identifier.
- Do not resolve a historical daily row using today's/latest base-info snapshot.
- Do not fall back to `ISU_NM`, abbreviation, English name or COMPANY identity when same-date code mapping is missing.
- If a daily-trade short code has no unique same-date base-info mapping, the stock market/date packet BLOCKS before DB write.
- Duplicate base-info mappings where one short code maps to multiple standard codes also BLOCK the packet.

## Stock market/date persistence boundary
A KOSPI or KOSDAQ stock packet consists of two already-validated provider responses for one `basDd`: base-info plus daily-trade.
Persist atomically in one DB transaction:
1. base-info Evidence snapshot,
2. SECURITY bootstrap/reuse and KRX external identifiers,
3. daily-trade Evidence snapshot,
4. supported Market Facts and FactAssertions.
If any DB step fails, none of those writes for that market/date packet commit.
KOSPI and KOSDAQ packets remain independent of each other.
