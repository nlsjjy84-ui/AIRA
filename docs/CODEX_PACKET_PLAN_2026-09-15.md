# AIRA Codex Packet Plan — 2026-09-15

## Operating rule
- Work from current local `C:\AIRA` working tree only.
- Preserve all pending/untracked work and `.tmp-witness-*`.
- Do not reopen CLOSED contracts unless current code/test exposes a concrete conflict.
- Each packet owns one main responsibility or two tightly coupled responsibilities.
- Do not run the full API regression between packets.
- Provider-targeted tests run only when a packet is ready to close.
- Full API regression runs once after all provider packets are coherent.
- No commit or push without explicit user instruction.

## Packet order
1. P1 — ECOS-31/32 close and orchestration
2. P2 — Provider-neutral Market Fact foundation + KRX SECURITY bootstrap
3. P3 — KRX daily stock snapshot ingestion
4. P4 — KRX index Evidence-only snapshots
5. P5 — OpenDART Historical Exact remaining integration
6. P6 — Material Event generic registration foundation
7. P7 — OpenDART Material Event approved adapters + orchestration
8. P8 — Provider bundle acceptance + final regression/closeout

## Stop rule
If a packet requires a semantic decision not already settled in the handoff contracts, stop that packet and report the exact missing decision instead of inventing it.
## P1 — ECOS-31/32
Primary docs: `ECOS_CODEX_HANDOFF_2026-09-15.md`, `ECOS_32_ORCHESTRATION_CONTRACT.md`.
- Start from current ECOS-31 implementation; do not redesign ECOS-1~30.
- Close quarterly REAL_GDP ingestion and implement only the thin scope -> validated observation -> persistence orchestration.
- Network read stays outside DB transaction; binding/Evidence/Fact/context/assertion persistence is atomic inside DB transaction.
- Preserve settled idempotency/conflict behavior; no scheduler, backfill, new metric, revision policy, UI/API expansion.
- CLOSED when ECOS targeted bundle and required PostgreSQL evidence pass with no failures/errors/skips.

## P2 — Market Fact foundation + KRX SECURITY
Primary docs: `MARKET_FACT_MODEL_V1_CONTRACT.md`, `KRX_SECURITY_BOOTSTRAP_CONTRACT.md`, `KRX_6_API_V1_CONTRACT.md`.
- Add provider-neutral daily Market Fact support without weakening Earnings or REAL_GDP invariants.
- Add only the eight settled stock predicates and their exact NUMBER/currency-or-unit semantics.
- Add SECURITY bootstrap using opaque internal identity plus explicit KRX external identifiers.
- Never use issuer/security name as identity and never infer COMPANY–SECURITY relation.
- Use additive migration only if schema checks require it; never edit previous migrations.
- CLOSED when domain/repository/PostgreSQL targeted tests prove shape, identity and conflict invariants.

## P3 — KRX daily stock ingestion
Primary docs: `KRX_6_API_V1_CONTRACT.md`, `KRX_INGESTION_ORCHESTRATION_CONTRACT.md`, `KRX_CODEX_HANDOFF_2026-09-15.md`.
- Implement validated KOSPI/KOSDAQ stock daily snapshot ingestion for explicit `basDd`.
- One market+dataset+date response -> one shared OFFICIAL_DATA Evidence snapshot.
- Resolve each row to canonical SECURITY through settled KRX identifiers; persist eight supported Market Facts/Assertions with deterministic locators.
- Validate the full response before DB write; one market+date persistence packet is atomic.
- Same request identity/same canonical response reuses Evidence; same identity/different content BLOCKS.
- CLOSED when same-run idempotency, row-order-independent hash, multi-security sharing and rollback tests pass.

## P4 — KRX index Evidence-only
Primary doc: `KRX_6_API_V1_CONTRACT.md`.
- Implement KOSPI/KOSDAQ index endpoint collection only as canonical OFFICIAL_DATA Evidence snapshots.
- Do not create index Entity or index Facts until a stable provider-native index identifier is officially validated.
- Do not use `IDX_NM` as canonical identity.
- Keep this packet isolated from stock Market Fact persistence.
- CLOSED when index response validation/hash/idempotency work without creating index entities/facts.
## P5 — OpenDART Historical Exact remaining integration
Primary docs: `OPENDART_CODEX_HANDOFF_2026-09-15.md`, `OPENDART_HISTORICAL_EXACT_TARGET_IDENTITY_CONTRACT.md`, `OPENDART_HISTORICAL_EXACT_EDGE_CONTRACT.md`, `OPENDART_HISTORICAL_EXACT_PERSISTENCE_CONTRACT.md`, `OPENDART_FILING_EVIDENCE_IDENTITY_CONTRACT.md`.
- Do not reopen 1A Fact Period Provenance, Filing Discovery, exact account mapping or period-witness semantics.
- Finish only the remaining validated filing identity -> filing Evidence -> period witness -> Fact/FactAssertion/FactPeriodEvidence integration.
- Treat `rcept_no` as filing identity; do not create competing filing Evidence rows for each representation of the same filing.
- Historical exact period is finalized by the matching CFS period witness; current `acc_mt` must not be projected backward across fiscal-year changes.
- Correction labels alone never create lineage, merge or supersession.
- CLOSED when original Evidence provenance, exact period, identity mismatch, short-period handling, idempotency and rollback targeted tests pass.

## P6 — Material Event generic registration foundation
Primary docs: `GENERIC_EVENT_REGISTRATION_V1_CONTRACT.md`, `OPENDART_MATERIAL_EVENT_REGISTRATION_CONTRACT.md`, `PROVIDER_INGESTION_COMMON_CONTRACT.md`.
- Extend Event creation/registration beyond the current EARNINGS-only path without changing EARNINGS semantics.
- Add provider-neutral generic Event registration for settled non-EARNINGS EventTypes only.
- Material Event dedup identity must follow the settled canonical provider/endpoint/company/receipt contract; title/name/collection time are not identity.
- CONFIRMED still requires canonical Entity and Evidence.
- Do not manufacture date-only provider values into fake precise timestamps.
- CLOSED when generic Event idempotency, entity/evidence requirements, dedup collision and existing EARNINGS regression targeted tests pass.

## P7 — OpenDART Material Event adapters + orchestration
Primary docs: `OPENDART_MATERIAL_EVENT_V1_CATALOG.md`, `OPENDART_MATERIAL_EVENT_FIELD_MATRIX_V1.md`, `OPENDART_MATERIAL_EVENT_REQUEST_CONTRACT.md`, `OPENDART_MATERIAL_EVENT_EVIDENCE_CONTRACT.md`, `OPENDART_MATERIAL_EVENT_EVIDENCE_FIELDS_CONTRACT.md`, `OPENDART_MATERIAL_EVENT_TRANSPORT_CONTRACT.md`, `OPENDART_MATERIAL_EVENT_GATE_CONTRACT.md`, `OPENDART_MATERIAL_EVENT_ORCHESTRATION_CONTRACT.md`.
- Implement only the approved v1 structured endpoints and settled GOVERNANCE/BUSINESS/RISK mappings.
- Discovery/title text alone never creates a confirmed Event.
- Validate provider identity and required endpoint fields before DB transaction.
- Persist each validated receipt atomically: Evidence -> Event -> COMPANY SUBJECT -> SUPPORTS Evidence -> CONFIRMED.
- New `rcept_no` remains new Evidence; do not infer correction lineage/merge/supersession.
- CLOSED when approved endpoint mapping, date-field rules, duplicate receipt idempotency, malformed BLOCK and transaction rollback tests pass.

## P8 — Combined acceptance / closeout
Primary docs: `AIRA_PROVIDER_ACCEPTANCE_MATRIX_2026-09-15.md`, `AIRA_CODEX_EXECUTION_PLAN_2026-09-15.md`.
- Run provider-targeted closing bundles only after P1-P7 implementation is coherent.
- Required PostgreSQL tests must execute, not silently skip; inspect result XML directly.
- Then run the full API regression exactly once.
- Run `git diff --check` once and audit additive migration ordering once.
- Confirm `.tmp-witness-*` and unrelated pending/untracked work are untouched.
- Report completed packets and genuine BLOCK items separately.
- No commit or push unless the user explicitly asks.
