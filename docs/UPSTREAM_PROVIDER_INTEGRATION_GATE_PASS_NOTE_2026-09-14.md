# Upstream provider integration gate — PASS

- Scope: the previously passing OpenDART exact receipt / FactPeriodEvidence and KRX canonical registration paths. ECOS remains HOLD because its prepared mapping payload was not recovered.
- One targeted PostgreSQL integration test passed with synthetic provider data. It persists an OpenDART filing and a KRX daily stock packet with the same display name and ticker, then replays both.
- `OPENDART/CORP_CODE` identifies only a COMPANY; `KRX/STANDARD_CODE` identifies only a distinct SECURITY. Neither provider's FactAssertions attach to the other's subject or Source. OpenDART filing and period-witness Evidence identities remain separate from `KRX_OPENAPI:{apiId}:{basDd}` Evidence.
- The filing Fact retains its exact fiscal period (`2025-04-01` to `2025-09-30`); the market Fact uses one trading date (`2040-01-08`) for both period boundaries. Identical replay keeps one Evidence per identity. Stored hashes equal the prepared provider-specific hashes, and Evidence URLs contain no authentication material.
- Provider-local changed-content and Fact value conflict behavior was already covered by the targeted OpenDART and KRX PostgreSQL suites; this gate verifies coexistence and replay, not live provider responses or a full regression.
- No migration or downstream/Current implementation changed for this gate.
