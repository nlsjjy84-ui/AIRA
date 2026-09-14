# AIRA Provider Acceptance Matrix — 2026-09-15

## Rule
A Packet is CLOSED only when its contract, persistence state, conflict behavior, idempotency, and required targeted evidence agree. Do not substitute a compile-only PASS for a required PostgreSQL invariant/E2E result.

| Packet | Validated input | Required persisted result | BLOCK conditions | CLOSED evidence |
|---|---|---|---|---|
| ECOS-31 Quarterly Fact Ingestion | validated `EcosRealGdpObservationResult`, approved REAL_GDP binding, collection time | reused ECOS Evidence; exact-quarter REAL_GDP Fact per numeric observation; `KRW_BILLION` statistical context; FactAssertion | blank/non-numeric value, series/subject/unit mismatch, Evidence identity conflict, invalid quarter | targeted domain + PostgreSQL ingestion test: repeat reuse, equal-value extra Evidence assertion, differing-value conflict, exact period/context, rollback |
| ECOS-32 Orchestration | `EcosRealGdpObservationScope` | network read outside DB tx; validated result persisted atomically through binding/evidence/fact ingestion | provider read/validation failure; any persistence invariant failure | orchestration integration test proves no DB partial state on persistence failure and repeat execution is idempotent |
| KRX Daily Snapshot/Fact Registration | one validated market endpoint response for explicit `basDd` | one shared `EXCHANGE/OFFICIAL_DATA` Evidence snapshot; approved Facts/assertions share it | unresolved instrument identity, unresolved metric/unit mapping, same Evidence identity with different canonical content | targeted canonical-hash/idempotency/order-independence test plus Fact registration/rollback test for only approved metrics |
| OpenDART Annual CFS / Historical Exact | discovered 14-digit `rcept_no`, explicit CORP_CODE mapping, validated annual CFS values, same-filing period witness | filing Evidence keyed by provider `rcept_no`; supported financial Facts; exact witness period; separate period-witness Evidence linked through `FactPeriodEvidence` | filing/witness identity mismatch, no single exact duration, ambiguous alias/account, value-period mismatch, legacy period mismatch, same Evidence identity with changed content | targeted adapter + persistence + PostgreSQL E2E proves same filing reuse, exact short/non-December period acceptance, mismatch BLOCK, rollback, period provenance |
| OpenDART Material Event v1 | validated row from one approved catalog endpoint and explicit company identity | filing Evidence + COMPANY link + one CONFIRMED Event using approved AIRA EventType and provider event date semantics | title-only classification, unknown endpoint, invalid receipt/company, missing required event date/fields, incompatible same receipt across adapters, unproved correction lineage | endpoint contract tests for all catalog adapters plus integration tests for same-receipt idempotency, correction/new-receipt separation, no auto-merge, rollback |

## OpenDART Material Event category acceptance
- GOVERNANCE adapters: capital increase/reduction and treasury-stock/trust endpoints from `OPENDART_MATERIAL_EVENT_V1_CATALOG.md`.
- BUSINESS adapters: CB/BW/EB, merger/split, business/asset/equity acquisition/disposal endpoints from the catalog.
- RISK adapters: business suspension, default, rehabilitation application, litigation.
- Each adapter must validate `rcept_no`, `corp_code`, its required structured fields, and its documented occurrence/decision date semantics.
- No generic `DISCLOSURE` fallback is accepted for an unresolved catalog mapping.

## Combined closeout
1. Provider-targeted bundles pass after implementation is complete.
2. Required real-PostgreSQL XML shows zero failures/errors/skips for tests that must execute on PostgreSQL.
3. Full API regression runs once after ECOS + KRX + OpenDART bundles are coherent.
4. `git diff --check` passes.
5. Existing migrations are unchanged; only justified additive migrations exist.
6. `.tmp-witness-*` and unrelated pending/untracked work remain preserved.
7. No commit/push occurs without explicit user instruction.

## Contract closure updates (2026-09-13)
- KRX implementation MUST follow `KRX_6_API_V1_CONTRACT.md`, `KRX_SECURITY_BOOTSTRAP_CONTRACT.md`, and `MARKET_FACT_MODEL_V1_CONTRACT.md`.
- KRX stock close condition includes: code-only identity join, SECURITY reuse/idempotency, eight Market Fact predicates with exact one-day periods, shared daily Evidence, conflict preservation, and index endpoints remaining Evidence-only.
- OpenDART material Event close condition includes: approved endpoint catalog only, provider-native `rcept_no` filing Evidence, provider-aware deterministic Event dedup, generic Event registration without changing EARNINGS semantics, COMPANY SUBJECT + SUPPORTS Evidence, and no fabricated timestamp from date-only provider fields.
- Any implementation that creates COMPANY from KRX names, index Entity from `IDX_NM`, Event from disclosure title alone, or overwrites conflicting Evidence/Fact is a hard acceptance failure.
