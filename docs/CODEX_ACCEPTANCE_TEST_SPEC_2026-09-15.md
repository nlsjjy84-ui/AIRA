# Codex Acceptance Test Spec — 2026-09-15

Purpose: define the minimum evidence required to close each implementation Packet without repeated full-suite testing.

## Global cadence
- Finish one coherent Packet implementation before running its targeted bundle.
- Do not run the full API suite after each Packet.
- Real-PostgreSQL tests that are contract evidence must execute with `failures=0 / errors=0 / skipped=0` before that Packet is CLOSED.
- Run the full API regression exactly once after the provider bundles are coherent.
- Existing CLOSED tests are regression guards, not invitations to redesign closed contracts.

## P1 — ECOS-31/32
Required evidence:
- validated quarterly observation maps `YYYYQn` to exact calendar quarter;
- identical ingestion is idempotent across Evidence/Fact/context/assertion;
- same quarter/same value/different Evidence adds assertion without duplicating Fact;
- same quarter/different asserted value preserves assertions and marks the Fact conflicting;
- blank/non-numeric observation is BLOCKED, not coerced to UNKNOWN/zero;
- HTTP observation read occurs outside the DB transaction and DB persistence is atomic;
- required PostgreSQL ECOS tests execute with zero skip/failure/error.
## P2 — KRX SECURITY + Market Fact model
Required evidence:
- SECURITY uses opaque internal canonical identity; KRX codes are external identifiers only;
- `KRX/STANDARD_CODE` cannot remap to another Entity;
- short codes are not registered in the global external-identifier registry; daily short codes resolve only through the same-market, same-`basDd` base-info join to STANDARD_CODE;
- a changed short code/name/market label reuses the same STANDARD_CODE SECURITY and never creates a second Entity;
- daily Market Fact subject must be SECURITY, `event=null`, NUMBER, `period_start=period_end=tradingDate`, `as_of_at=null`;
- price/trading-value/market-cap Facts require `KRW`; trading-volume/listed-shares require no currency;
- dedup identity is provider-neutral `SECURITY + predicate + trading date`;
- same Fact with different asserted values becomes CONFLICTING while preserving assertions;
- existing Earnings and REAL_GDP shape/invariant tests remain unchanged and pass.

## P3 — KRX stock ingestion
Required evidence:
- base-info and daily-trade requests use the exact same `basDd`;
- daily `ISU_CD` resolves only through same-market base `ISU_SRT_CD`, never name matching;
- one base-info endpoint/date response -> one shared Evidence snapshot;
- one daily-trade endpoint/date response -> one shared Evidence snapshot;
- daily response row order does not change canonical content hash;
- identical rerun reuses Evidence, SECURITY, Facts and assertions;
- same Evidence identity with changed canonical content BLOCKS without overwrite/revision bump;
- provider `-`/absent numeric metric creates no Fact for that metric and is never coerced to zero;
- malformed identity/join data blocks the market/date packet before partial DB persistence;
- injected DB failure proves the market/date persistence packet rolls back atomically.
## P4 — KRX index Evidence-only
Required evidence:
- `kospi_dd_trd` and `kosdaq_dd_trd` register/reuse OFFICIAL_DATA Evidence snapshots only;
- no index Entity, Fact, Event or Assessment is created in v1;
- `IDX_NM`/`IDX_CLSS` are never promoted to canonical identity;
- identical snapshot rerun is idempotent and row order does not change hash;
- same snapshot identity with changed canonical content BLOCKS.

## P5 — OpenDART Historical Exact integration
Required evidence:
- Filing Discovery remains transport/validation only and performs no DB write;
- Filing Discovery produces the expected validated 14-digit `rcept_no`, and every downstream annual CFS/period-witness representation must match that exact receipt before persistence;
- account selection filters by the expected discovered receipt before applying approved account-id mapping; another receipt is never silently adopted as the target;
- exact filing identity uses validated 14-digit `rcept_no` and explicit canonical COMPANY mapping;
- current `acc_mt` is never retroactively projected across uncertain historical fiscal-year changes;
- exact historical period comes from the already-approved filing-specific period witness path;
- short/changed fiscal periods are preserved when officially witnessed; no forced 12-month assumption;
- missing/malformed/identity-mismatched/conflicting period witness BLOCKS without guessed period;
- approved account mapping rules remain exact; no `account_nm` inference;
- provider HTTP/discovery/value/witness preparation occurs outside the DB write transaction;
- one filing persistence transaction atomically covers all approved metric writes, filing Evidence, period-witness Evidence and every FactPeriodEvidence link;
- nested metric ingestion participates in the filing transaction and must not independently commit a subset of metrics;
- repeated identical filing/period ingestion is idempotent and injected failures prove no partial metric/provenance/period-link writes survive.
## P6 — Generic Event registration foundation
Required evidence:
- existing EARNINGS creation/registration semantics remain unchanged;
- generic registration accepts only already-approved non-EARNINGS EventTypes;
- deterministic dedup key reuses one Event under concurrent/same-input registration;
- dedup identity never depends on title, company display name, collection time, response order or asserted values;
- dedup collision with incompatible event type/provider identity BLOCKS rather than mutating the existing Event;
- generic Event remains CANDIDATE until canonical SUBJECT Entity and SUPPORTS Evidence links exist;
- confirmation without either required link fails;
- repeated SUBJECT/SUPPORTS link creation is idempotent;
- date-only provider values are not converted to invented timestamps;
- existing EARNINGS targeted regression remains PASS.

## P7 — OpenDART Material Event v1 adapters/orchestration
Required evidence:
- exactly the approved 24 structured endpoint contracts are implemented; no title-based or catch-all fallback creates Events;
- request scope requires explicit 8-digit corp code plus valid `YYYYMMDD` begin/end dates with `begin<=end` and no implicit scheduler/lookback/default-date behavior;
- overlapping request windows returning the same receipt reuse the same Evidence/Event identities; request window never participates in receipt identity;
- endpoint-to-EventType mapping matches the settled GOVERNANCE/BUSINESS/RISK catalog;
- every row validates 8-digit `corp_code` and 14-digit `rcept_no` before persistence;
- COMPANY resolution uses only existing `OPENDART/CORP_CODE` mapping; missing/incompatible mapping BLOCKS;
- endpoint-specific official date field rules match `OPENDART_MATERIAL_EVENT_FIELD_MATRIX_V1.md`, including `piicDecsn` having no invented occurrence date;
- provider status `013` is handled as NO_DATA, while auth/rate-limit/invalid/provider failures are not silently converted to empty success;
- API key/auth material never appears in Evidence content/hash/provenance, logs or user-visible exception text;
- structured Evidence `originalUrl` is the stable official endpoint base URL without auth/search-window query values; locator deterministically contains endpoint key + receipt;
- `publishedAt` remains null unless OpenDART supplies an actual publication timestamp; it is never inferred from `rcept_no`, decision date or collection time;
- canonical Evidence hash covers the complete validated row with deterministic field ordering and excludes transport wrapper/auth/collection-time material;
- each validated structured row registers/reuses `OFFICIAL_DATA` Evidence with deterministic `OPENDART_MATERIAL:{endpointKey}:{rcept_no}` identity;
- raw filing `DISCLOSURE / rcept_no` Evidence remains a separate provenance responsibility and is not overwritten by material structured payloads;
- same structured Evidence identity + identical canonical content reuses; changed content/provenance BLOCKS without overwrite/revision bump;
- one validated receipt persists atomically as Evidence -> Event -> COMPANY SUBJECT -> SUPPORTS -> CONFIRMED;
- same receipt rerun creates no duplicate Event/EventEntity/EventEvidence rows;
- same `rcept_no` classified by incompatible approved endpoint contracts BLOCKS rather than creating competing Events;
- different receipts that may describe one real-world incident remain separate v1 Events unless explicit official lineage exists;
- injected persistence failure rolls back all writes for that receipt only and does not affect already-closed unrelated receipts.

## P8 — Combined acceptance / closeout
Required evidence:
- P1-P7 are individually CLOSED before the final combined run;
- provider-targeted closing bundles pass without reopening already-closed contracts;
- every required PostgreSQL acceptance test actually executes; fresh XML shows `failures=0 / errors=0 / skipped=0`;
- full API regression runs exactly once after provider bundles are coherent and passes;
- `git diff --check` passes once at final closeout;
- migration audit confirms existing migrations are unchanged and any new migration is additive, ordered and justified;
- `.tmp-witness-*` plus unrelated pending/untracked work remain untouched;
- secrets are absent from tracked files, test fixtures, logs and Evidence provenance;
- completed Packets and genuine BLOCK items are reported separately;
- no commit or push occurs without explicit user instruction.

## Final CLOSED rule
The combined provider milestone is CLOSED only when P1-P8 satisfy the evidence above. A skipped required real-DB test, unresolved provider identity ambiguity, invented fallback semantics, or unreviewed migration change keeps the affected Packet OPEN/BLOCKED rather than being waived.