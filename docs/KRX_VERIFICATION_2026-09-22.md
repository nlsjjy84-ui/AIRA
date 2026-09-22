# KRX verification — 2026-09-22

## Verified before live integration
- Remote device: JYJ; repository C:\AIRA. Existing pending edits preserved.
- Explicit provider date: 2026-09-21.
- All six production endpoints returned HTTP 200 and nonempty OutBlock_1.
- Stock base/daily counts: KOSPI 942 each; KOSDAQ 1818 each.
- Index response counts: KOSPI 54; KOSDAQ 40.
- Targeted KRX tests: 28 tests, 0 failures/errors, 0 skipped (PostgreSQL included).
- Rollback fixture corrected: blank name failed before persistence; a 301-character name now passes packet preparation and fails inside persistence, verifying Evidence rollback.

## Live integration — PASS
- Test: KrxLiveVerificationTests; opt-in AIRA_KRX_LIVE_VERIFY=true.
- Explicit date 2026-09-21; isolated database aira_krx_live_test.
- Real HttpKrxClient, packet validation, KrxPersistence, and PostgreSQL were used.
- JUnit: tests=1, failures=0, errors=0, skipped=0.
- Six Evidence snapshots, 2,760 SECURITY entities, and 22,086 Facts/Assertions persisted.
- Every promoted stock value was compared with its stored Fact value.
- Identical replay left all Source/Evidence/Entity/Fact/Assertion counts unchanged.

## Scope discrepancy retained for review
- Sep 13/15 contracts describe index Evidence-only ingestion.
- Sep 21 commit 1491672 adds representative KOSPI/KOSDAQ Market Facts and V21.
- Current implementation is preserved; this verification does not authorize every index series as a canonical Fact.
- No production database writes, migration edits, commit, or push in this verification.
