# Auth / Interest eligibility partial PASS — 2026-09-14

> The KOSPI/KOSDAQ common-stock market HOLD here was resolved by `KRX_CURRENT_INTEREST_ELIGIBILITY_PASS_NOTE_2026-09-14.md`.

Auth KEEP: Argon2id password hashing; SHA-256 hash of raw session/reset tokens; 30-minute idle and 12-hour absolute session limits; 30-minute reset token; successful reset transaction revokes every active session; `/api/me` and `/api/me/**` require authentication. No WiFi/Bluetooth identifier or connection collection path was found in API source/resources.

Interest KEEP: a user interest points to an exact canonical Entity ID and type; no COMPANY↔SECURITY propagation occurs. Existing interests are returned before new-registration validation, remain listable/deliverable, and can be removed. The current v1 new-registration gate accepts only active COMPANY; all new SECURITY registrations, including ETF/ETN and other hard-excluded instruments, remain closed without making assumptions from a security name. Raw KRX ingestion and Evidence are not filtered by interest policy.

Partial HOLD: there is no repository contract for establishing the latest completed official KRX trading day. Therefore the common-stock NEW Interest close `<1000` (exactly 1000 allowed) and daily rise `>=20%` gate cannot be safely evaluated. There is also no approved structured instrument-classification input here. Do not use D-1, latest observed Fact, names, or raw-ingestion filters as substitutes. Existing SECURITY interests, if any, remain untouched; delete/re-add is blocked while this gate is closed.

Targeted verification: Auth security 7, session integration 2, account recovery PostgreSQL 2, CSRF/private-route 15, Interest service 12, Interest concurrency PostgreSQL 1 passed. Browser cleanup PostgreSQL test was skipped because its dedicated two-user fixture credentials were not supplied. Disposable PostgreSQL Flyway version 17 is successful. No migration changed.
