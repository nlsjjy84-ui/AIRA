# 12D visualization read API — PASS

- Added Financial Historical Exact A/B read through the existing verified-period query, with all-or-nothing requested metrics, exact receipt/period/Evidence identities, backend B−A amount, and percent only for a positive A baseline.
- Added stored official KRX range series and explicit D Fact versus previous actual stored observation. Neither endpoint calls a provider or alters the existing Current D resolver. A missing exact D Fact never selects a preceding point as Current.
- Added public GET routing and a 12C integration contract. No Fact write path, provider adapter, migration, or Current selection policy changed.
- Targeted unit, HTTP, and PostgreSQL tests: 8 passed, 0 failures. Included the existing provider integration gate. Temporary PostgreSQL Flyway history ends at successful V17.
