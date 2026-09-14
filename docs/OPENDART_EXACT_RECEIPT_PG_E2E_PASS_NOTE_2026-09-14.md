# OpenDART exact receipt + FactPeriodEvidence PostgreSQL E2E — PASS

- Fresh disposable PostgreSQL 18 cluster: `C:\AIRA\.tmp-witness-1b-db`, database `aira_witness_test`, port `55432`. Only synthetic local test credentials were used.
- Flyway on the fresh database: V1 through V15 applied successfully and recorded `success=true` for every version. Existing migration files were not changed.
- Targeted Gradle run: 79 tests, 0 failures, 0 skipped. The selection covered exact receipt discovery/adapter validation, annual CFS period witness resolution, FactPeriodEvidence schema/registration, filing persistence and idempotency, transaction rollback, and concurrent identical/conflicting filing cases.
- Required test correction: the isolated V10 upgrade assertion now expects five migrations (V11–V15), matching the current migration chain. The earlier shared-DB failure was a stale schema state compounded by that obsolete assertion; a clean database passed.
- No ECOS work or full regression was run. Existing worktree changes and the disposable database remain in place.
