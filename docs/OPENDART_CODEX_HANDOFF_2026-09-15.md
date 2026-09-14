# OpenDART / Historical Exact — Codex Handoff 2026-09-15

## Do not reopen
- `AIRA 기준 명세 v1.0` remains the authority.
- 1A Fact Period Provenance Foundation is closed: provider-neutral `FactPeriodEvidence`, additive V11, required locator, idempotent same link, conflicting locator rejected, period evidence excluded from public assertions.
- Filing Discovery contract is already settled; do not redesign list pagination/identity rules.
- Fiscal period derivation is settled: never infer `bsns_year=YYYY` as a calendar fiscal year. For Historical Exact, the matched filing-specific annual CFS period witness is the final authority for exact period start/end; current `company.json.acc_mt` is only current official metadata for discovery/plausibility and MUST NOT override or be projected backward across a filing-specific historical witness. Missing/malformed/conflicting witness data BLOCKS rather than guessing.
- Period witness semantics are settled: annual CFS witness, one exact period, explicit malformed/missing/conflict/identity-mismatch BLOCK conditions, provenance hash and locator/evidence linkage.

## Settled Filing Discovery contract
- Official `list.json` only for this responsibility.
- `corp_code`: 8 digits.
- `rcept_no`: 14 digits.
- `rcept_dt`: `YYYYMMDD`.
- `last_reprt_at=N`.
- `page_count` maximum 100; deterministic full pagination.
- `sort=date`, `sort_mth=asc`.
- Pipeline boundary: HTTP page client -> transport DTO -> strict validation -> full pagination -> receipt dedup -> deterministic sort -> validated filing result.
- Filing Discovery itself performs no Evidence/Event/Fact/DB write.
## Correction / lineage restraint
- Official `report_nm` markers such as `[기재정정]` and `rm` values such as `정/철` may exist.
- They are provider metadata, but are not by themselves sufficient to infer correction lineage, Event merge, supersession, or automatic replacement.
- Do not invent lineage or merge semantics from those labels.

## Codex implementation bundle
- Start from the current local working tree and preserve all pending/untracked work.
- Inspect only OpenDART files relevant to the unfinished bundle once; do not re-audit closed 1A/discovery/period-witness contracts.
- Complete the remaining connection from validated filing discovery/identity through original official Evidence provenance and the already-settled period witness path.
- Complete material Event common registration only according to already accepted Event semantics; do not create new merge/correction rules.
- Complete approved-subset adapters and HTTP secret/failure hardening that are already specified in existing contracts/docs.
- Reuse existing shared Evidence and transaction/rollback behavior; do not create parallel provenance models.
- Existing exact account mapping rules remain authoritative, including the approved `ifrs-full_Revenue -> REVENUE` alias and prohibition on `account_nm` inference.
## Verification / efficiency rule
- Do not run full regression after each OpenDART file or sub-responsibility.
- Finish one coherent implementation bundle first.
- Then run targeted OpenDART/Historical Exact tests once, including identity/idempotency, original Evidence provenance, period witness blocking, rollback, and approved adapter semantics.
- Run the full API regression once only at the end of the combined provider work unless a real cross-cutting conflict appears.
- Do not modify old migrations; add only a genuinely required additive migration.
- Preserve `.tmp-witness-*` and unrelated pending/untracked work.
- No commit or push without explicit user instruction.

## BLOCK rule
- If the current local working tree contains behavior that conflicts with these settled contracts, report the exact conflict and affected files before changing semantics.
- If a required downstream rule is not already settled in AIRA docs/code, BLOCK instead of inventing it.

## 2026-09-13 material-event closure update
Use these additional contracts as authoritative:
- `OPENDART_MATERIAL_EVENT_GATE_CONTRACT.md`
- `OPENDART_MATERIAL_EVENT_V1_CATALOG.md`
- `OPENDART_MATERIAL_EVENT_REGISTRATION_CONTRACT.md`

The registration contract clarifies implementation precision: approved provider date-only fields remain validated Evidence data but MUST NOT be coerced into a fabricated `OffsetDateTime`; with the current schema `Event.occurred_at` remains null for date-only precision. Material Event registration must use the new additive generic Event path rather than weakening or rewriting the existing EARNINGS path.

Also use `OPENDART_MATERIAL_EVENT_ORCHESTRATION_CONTRACT.md` for network/validation/transaction boundaries. Material-event HTTP stays outside DB transactions; each validated `rcept_no` persists atomically and independently. `OPENDART/CORP_CODE -> COMPANY` mapping is a precondition for this ingestion path; if missing/incompatible, BLOCK and use the separate approved company-bootstrap responsibility outside Material Event ingestion.
## 2026-09-13 official field-matrix closure
- The selected Material Event v1 catalog contains 24 endpoints: GOVERNANCE 8 + BUSINESS 12 + RISK 4.
- Implement endpoint adapters against `OPENDART_MATERIAL_EVENT_FIELD_MATRIX_V1.md`; do not rediscover date fields during Codex work.
- The matrix is authoritative for provider apiId and semantic event-date field selection.
- `piicDecsn` is an explicit official-schema exception: no documented decision-date field exists, so no date may be inferred.
- Date-only provider values remain Evidence/adapter metadata; do not manufacture `occurred_at` timestamps.
## 2026-09-13 Material Event Evidence split
- Material Event adapters must use `OPENDART_MATERIAL_EVENT_EVIDENCE_CONTRACT.md`.
- Mandatory Event SUPPORTS Evidence is endpoint-specific structured `OFFICIAL_DATA`, not the raw filing DISCLOSURE Evidence content slot.
- Deterministic structured Evidence identity is `OPENDART_MATERIAL:{endpointKey}:{rcept_no}`.
- Existing raw filing `DISCLOSURE / external_id=rcept_no` semantics stay unchanged for filing provenance.
## 2026-09-13 generic Event foundation closure
- Use `GENERIC_EVENT_REGISTRATION_V1_CONTRACT.md` for Packet P6.
- Preserve accepted EARNINGS behavior while adding a generic non-EARNINGS registration primitive.
- Same dedup key with incompatible EventType/title is a contract conflict, not an update opportunity.
- Material Event confirmation remains Entity + structured Evidence gated and transactional.
## 2026-09-13 Historical Exact persistence closure
- Use `OPENDART_HISTORICAL_EXACT_PERSISTENCE_CONTRACT.md` for P5 transaction boundaries.
- Discovery, annual CFS fetch, period-witness fetch and any approved bootstrap HTTP stay outside the DB write transaction.
- One filing-level persistence transaction atomically covers all approved metric writes, filing Evidence, period-witness Evidence and FactPeriodEvidence links under the existing company advisory lock.
- Nested metric ingestion must participate in that filing transaction; no per-metric independent commit is allowed.
## 2026-09-13 Historical Exact target-identity closure
- Use `OPENDART_HISTORICAL_EXACT_TARGET_IDENTITY_CONTRACT.md` for P5 filing identity propagation.
- Filing Discovery selects the expected provider-native `rcept_no`; downstream annual CFS rows and the period witness must match that exact receipt.
- Filter by expected receipt before account-id selection. Do not adopt another receipt merely because the structured API returned it.
- The expected receipt is an AIRA validation constraint only; do not invent a provider request parameter that OpenDART does not expose.
