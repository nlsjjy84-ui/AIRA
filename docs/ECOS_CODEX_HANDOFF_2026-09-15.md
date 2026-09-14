# ECOS Codex Handoff — 2026-09-15

## Fixed baseline
- AIRA 기준 명세 v1.0 is the top-level authority.
- ECOS-1~30 are CLOSED. Do not re-audit or redesign them unless a concrete conflict is observed.
- ECOS-30 closed with real PostgreSQL evidence: tests=3, failures=0, errors=0, skipped=0.
- V14 remains unchanged; the fact_statistical_context identity-retention gap is fixed additively by V15.
- Existing OpenDART earnings semantics must remain unchanged.
- Existing pending/untracked work and `.tmp-witness-*` must be preserved.
- No commit/push unless explicitly requested by the user.

## ECOS-31 current state
- Goal: validated quarterly REAL_GDP observation -> provider-neutral Fact ingestion.
- Core implementation exists: exact quarter mapping, ingestion service/result, ADR-013, targeted tests.
- compileJava PASS and testClasses PASS.
- Do not add Event, Assessment, new observation table, automatic revision, or supersession here.
- Same request identity with different snapshot content must BLOCK via existing Evidence contract.
- Same quarter + same value may add/reuse assertion; different value must not overwrite and must preserve conflict evidence.

## What NOT to do before Codex
- No repo-wide reread.
- No full regression for each small responsibility.
- No re-running CLOSED packet tests.
- No broad refactor of Fact/Evidence/Series models.
- No scheduler, historical backfill engine, or macro-event generation yet.
## Codex Task A — close ECOS-31 efficiently
1. Inspect only ECOS-31 new files and their direct dependencies.
2. Run ECOS-31 targeted tests once, including PostgreSQL integration with DB_PASSWORD available in the Codex environment.
3. If PASS, do not revisit ECOS-31 internals.
4. If failure is a true contract conflict, fix only the implicated responsibility and rerun targeted tests once.

## Codex Task B — ECOS-32 orchestration
Create one thin application service for:
`EcosRealGdpObservationScope -> ObservationReader -> validated result -> series binding/evidence reuse -> fact ingestion -> receipt/result`.

Contract:
- No duplicate validation logic already owned by reader/parser/evidence/binding layers.
- One explicit transaction boundary for persistence; provider HTTP read must not be hidden inside a long DB transaction.
- Reuse existing idempotency/conflict rules; do not invent revision/supersession policy.
- Return stable IDs/counts sufficient to prove what was reused/created; do not expose provider secrets.
- Failure must leave no partial Fact/context/assertion persistence.

## Codex Task C — integration proof
Add focused tests for:
- one exact quarter and multi-quarter ingestion;
- identical rerun idempotency;
- overlapping Evidence with same value;
- overlapping Evidence with conflicting value -> CONFLICTING, both assertions retained;
- blank numeric observation -> BLOCK with no partial write;
- exact YYYYQn -> calendar quarter boundaries;
- no Event/Assessment creation.

Do not create broad UI/API work in this packet.
## Codex Task D — close the bundle once
After Tasks A-C are green:
1. Run one ECOS bundle targeted test set.
2. Run full API regression exactly once.
3. Run `git diff --check` once.
4. Audit migration list once; existing migrations must not be rewritten.
5. Confirm `.tmp-witness-*` and unrelated pending/untracked work are untouched.
6. Report XML/result counts directly.

## Deferred beyond this bundle
Defer unless separately designed:
- scheduled collection cadence;
- broad historical backfill/resume/checkpoint engine;
- provider revision/supersession semantics;
- additional ECOS macro indicators;
- public API/UI exposure;
- performance/batch optimization beyond demonstrated need.

## Efficiency rule
For each next packet: establish responsibility and invariants first. Implement related files as one bundle. Test when the packet closes, not after every file. Re-open a CLOSED area only on a concrete failing test or semantic conflict.
