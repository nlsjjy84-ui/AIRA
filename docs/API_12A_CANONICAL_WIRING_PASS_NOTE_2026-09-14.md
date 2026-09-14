# 12A API canonical wiring — PASS

- Added neutral Company/Security identity search with typed canonical IDs and keys; no entity conversion or recommendation ranking.
- Added separate Financial Historical Exact and explicit Financial Current reads. Responses carry selection, exact period, receipt, Fact Evidence identity, and a distinct canonical state. No latest-period inference or fallback was added.
- Added supersession-terminal Assessment Current and exact official KRX D market Current reads. A missing KRX target Fact retains the discovered D in the NO_DATA response. An Event with no Assessment remains without one.
- Added private Interest eligibility read keyed by authenticated user and canonical target. Existing Interest remains visible independently of the new-registration gate.
- State vocabulary is AVAILABLE, NO_DATA, PARTIAL, STALE, CONFLICTING, BLOCKED, UNSUPPORTED, UNAVAILABLE. STALE is reserved until an approved freshness threshold exists; it is not inferred from timestamps.
- Targeted API/service/PostgreSQL tests passed (25 tests, 0 failures), including the absent P7 Assessment response. Disposable PostgreSQL Flyway history ended at successful V17. No migration changed.
