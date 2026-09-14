# KRX Current + NEW Interest eligibility PASS NOTE — 2026-09-14

Scope: existing KOSPI/KOSDAQ SECURITY identities and the approved `stk_bydd_trd` / `ksq_bydd_trd` stock-daily families. KONEX, ETF/ETN, and derivatives remain outside this packet; no name keywords classify products.

`KrxLatestCompletedTradingDayResolver` starts at the KST current date and inspects official daily snapshots in descending calendar-date request order. It accepts the first fully validated non-empty market snapshot as D. The 14-date request budget bounds an unavailable provider; exhaustion fails closed. The resolver does not examine a target security, infer weekdays/holidays, or substitute a previous target Fact. A malformed candidate blocks rather than being skipped.

`KrxCurrentQuery` returns only a SUPPORTED exact D market Fact asserted by the same KRX stock-daily Evidence identity and content hash. If that SECURITY has no D Fact, Current is unavailable even when it has a D-1 Fact. NEW Interest additionally verifies exact same-date base/daily standard-code mapping and the official `FLUC_RT` row. Close below KRW 1,000 or daily change at/above +20% blocks; close exactly KRW 1,000 with change below +20% is allowed. Existing Interest lookup precedes eligibility, preserving existing rows and delivery; removal remains available. No raw KRX ingestion or Evidence filtering was added.

Targeted verification: KRX packet/resolver 5, KRX PostgreSQL persistence/Current/Interest 8, Interest unit 13, Interest concurrency PostgreSQL 1 passed. Disposable PostgreSQL Flyway version 17 is successful; no migration changed. No live KRX credential or call was used.
