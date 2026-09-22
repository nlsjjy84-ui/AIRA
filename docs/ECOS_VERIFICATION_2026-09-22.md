# ECOS REAL_GDP verification — 2026-09-22

## Result
- Existing ECOS bundle plus ECOS-32 orchestration tests passed.
- Real BOK ECOS StatisticSearch returned 2025Q1 and 2025Q2 for 200Y104/1400/Q.
- Live integration JUnit: tests=1, failures=0, errors=0, skipped=0.
- One Evidence snapshot and two exact-quarter REAL_GDP Facts/Assertions persisted.
- Stored values matched official DATA_VALUE values and identical replay was idempotent.

## Orchestration guarantees
- Provider HTTP completes outside the database transaction.
- Existing transactional ingestion owns series binding, Evidence, Fact, context, and assertion writes.
- Registered Evidence is checked against the validated request identity and snapshot provenance.
- A forced database failure on the second quarter rolled back the first quarter and Evidence.
- Provider failure produces no writes; Event and Assessment counts remain unchanged.

## Scope
- This closes the REAL_GDP ECOS-31/32 path only.
- Scheduler, historical backfill policy, revisions/supersession, other indicators, and UI remain deferred.
- No production database was used and no commit or push was performed.
