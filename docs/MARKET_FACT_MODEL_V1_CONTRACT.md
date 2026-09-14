# Provider-neutral Market Fact Model v1 Contract

## Purpose
Support exact daily exchange observations without coupling Fact semantics to KRX transport DTOs.

## v1 predicates
- `OPEN_PRICE`
- `HIGH_PRICE`
- `LOW_PRICE`
- `CLOSE_PRICE`
- `TRADING_VOLUME`
- `TRADING_VALUE`
- `MARKET_CAP`
- `LISTED_SHARES`

## Subject and time
- subject Entity type MUST be `SECURITY`.
- Event MUST be null for ordinary daily exchange observations; price movement itself is not an Event.
- `period_start` and `period_end` MUST both equal the exact trading date supplied by official Evidence.
- `as_of_at` is not a substitute for trading date and must not be synthesized from collection time.
## Value semantics
- `OPEN_PRICE`, `HIGH_PRICE`, `LOW_PRICE`, `CLOSE_PRICE`: numeric monetary price, currency required.
- `TRADING_VALUE`, `MARKET_CAP`: numeric monetary amount, currency required.
- `TRADING_VOLUME`, `LISTED_SHARES`: numeric share count, currency MUST be null.
- Unit meaning is fixed by predicate in v1; do not add provider-specific unit strings to Fact.
- Negative values are invalid for these eight v1 predicates.
- Zero may be valid if the official provider returns zero; zero is not missing.

## Dedup identity
The provider-neutral dedup key MUST include:
- canonical SECURITY identity,
- Fact predicate,
- exact trading date,
- semantic currency/unit token fixed by the predicate contract.
Provider name, request URL, Evidence ID, display name, and collection time MUST NOT define Fact identity.
## Provenance and conflict
- A SUPPORTED Market Fact requires at least one `FactAssertion` to official Evidence.
- Same Fact + same Evidence is idempotent.
- A second admissible Evidence asserting the same value adds provenance without replacing the Fact.
- A second admissible Evidence asserting a different value marks the Fact `CONFLICTING` and preserves both assertions.
- Do not auto-average, prefer the latest, or overwrite.

## Compatibility rule
- Existing earnings and REAL_GDP Fact factories/guards remain unchanged.
- Implement Market Fact support as an additive domain path, not by weakening existing predicate/type checks.
- No schema migration is required solely to represent the eight v1 predicates if current numeric/currency/period columns are sufficient.
- If implementation discovers a lossless-representation gap, BLOCK and report it before adding schema.

## Explicitly excluded
- derived return/change-rate Facts,
- adjusted-price history,
- index Facts,
- COMPANY subjects for exchange price observations,
- provider-specific Fact subclasses.
## Daily Market Fact identity
Market Fact dedup is provider-neutral. Canonical identity is:
`AIRA|FACT|V1|MARKET_DAILY|{subjectSecurityCanonicalKey}|{predicate}|{tradingDate}`.
Hash the canonical identity with SHA-256 for `dedup_key`.
Do not include KRX/source/evidence ID, market display name, collection time or value in the dedup identity.

For all v1 daily Market Facts:
- subject Entity must be `SECURITY`.
- `event_id` is null; ordinary market observation is not an Event.
- `period_start = BAS_DD` and `period_end = BAS_DD`.
- `as_of_at` is null because the provider contract supplies a trading date, not an exact observation timestamp.
- value type is `NUMBER`.

## Predicate value shape
- `OPEN_PRICE`, `HIGH_PRICE`, `LOW_PRICE`, `CLOSE_PRICE`: numeric, `currency_code=KRW`.
- `TRADING_VALUE`, `MARKET_CAP`: numeric, `currency_code=KRW`.
- `TRADING_VOLUME`, `LISTED_SHARES`: numeric integral count, `currency_code=null`.
- Do not convert missing provider tokens to zero.
- Parsed numeric values must be non-negative for this v1 KRX scope; malformed/negative provider values BLOCK the snapshot rather than being normalized.

## Multi-Evidence semantics
If another valid Evidence asserts the same SECURITY/predicate/trading-date value, reuse the same Fact and add/reuse its FactAssertion.
If valid Evidences assert different values for the same dedup identity, preserve both assertions and apply the existing Fact conflict semantics; never overwrite one source with another.
