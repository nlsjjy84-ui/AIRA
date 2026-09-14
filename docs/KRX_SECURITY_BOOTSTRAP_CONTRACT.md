# KRX SECURITY Bootstrap Contract

## Internal identity
- Create/reuse `EntityType.SECURITY`, never COMPANY, for KRX listed-stock observations.
- Internal `MarketEntity.canonical_key` remains opaque (`SECURITY:{opaque-id}` style); do not embed KRX codes into the internal canonical key.
- On first SECURITY creation, `canonical_name` = official base-info `ISU_NM`; `ISU_ABBRV`/`ISU_ENG_NM` are not fallback identities or fallback canonical names.
- If `ISU_NM` is blank/malformed and no existing STANDARD_CODE mapping exists, BLOCK new SECURITY creation rather than invent a name.
- `canonical_name` is display metadata, not identity; later name differences do not create a new SECURITY.
- `market_code` = endpoint market (`KOSPI` or `KOSDAQ`) at first creation only; it is convenience metadata, not historical truth.
- `symbol` = the base-info short code at first creation only, for display/convenience; identity lookup still uses external identifiers.
- `country_code` = `KR`.

## External identifiers
The only v1 registry identity is:
- namespace `KRX`
- identifier type `STANDARD_CODE`
- value = base-info `ISU_CD` (provider standard code)

`ISU_SRT_CD` is NOT registered in `entity_external_identifier`. It is a dated ticker/join/display value preserved in the same-`basDd` KRX Evidence and may initialize `entity.symbol` as convenience metadata only.
Names and short codes are never canonical registry identities in v1.
## Registration / reuse
- Resolve by `KRX/STANDARD_CODE` only.
- Existing mapping must point to `EntityType.SECURITY`; otherwise BLOCK.
- If absent, create one SECURITY entity and register only the standard code in one DB transaction.
- Re-running the same base-info row is idempotent.
- A changed `ISU_SRT_CD`, name, or market label never remaps the STANDARD_CODE identity and never creates a second SECURITY.
- Daily rows are resolved before persistence by joining `daily.ISU_CD` to the same-market, same-`basDd` base-info `ISU_SRT_CD`, yielding the provider standard code; the short code itself is never looked up as a global external identifier.

## Compatibility hardening
Extend `ExternalIdentifierKey` / registry compatibility only additively:
- `KRX/STANDARD_CODE` may identify only SECURITY.
- No KRX short-code identifier type is added in v1.
- Keep the existing OpenDART CORP_CODE -> COMPANY rule unchanged.

## COMPANY relation
No COMPANY–SECURITY relation is created in this packet. OpenDART company identity and KRX security identity remain separate until an explicit official mapping contract exists.
## Exact external identifier key
Follow ADR-006 and keep provider identifiers out of `entity.canonical_key`.
Use exactly:
- base-info `ISU_CD` (표준코드) -> `ExternalIdentifierKey("KRX", "STANDARD_CODE", value)`.

Do not register base-info `ISU_SRT_CD` or daily-trade `ISU_CD` in the external identifier registry; both are short-code/ticker values in this flow.
Do not normalize leading zeroes, case, or length beyond explicit provider validation; preserve the standard-code text exactly.

## SECURITY metadata policy
- New SECURITY receives an opaque internal canonical key, never a KRX identifier as canonical key.
- On first creation, official base-info `ISU_NM` initializes `canonical_name`; name is not identity and no abbreviated-name fallback is used.
- `market_code` may record `KOSPI` or `KOSDAQ` and `symbol` may record the official short code as convenience metadata only; identity lookup/idempotency uses only `KRX/STANDARD_CODE`.
- `country_code` is `KR` for these KRX-listed securities.
- If an existing STANDARD_CODE is found with a changed provider name or short code, reuse the same SECURITY; do not create a second Entity or remap identity solely because display metadata changed.
- Do not infer or create COMPANY–SECURITY ownership/issuer relations in this packet.
## Historical market-membership restraint
`entity.market_code` and `entity.symbol` are non-authoritative convenience metadata; they do not encode historical market membership.
- A SECURITY already identified by `KRX/STANDARD_CODE` is reused even if a later/same historical dataset shows a different short code or market label.
- Do not create a second SECURITY solely because KOSPI/KOSDAQ membership differs across dates.
- Do not automatically overwrite static `market_code` or `symbol` to pretend they are time-series history.
- Historical market membership and short-code observations remain grounded in the dated KRX Evidence snapshots, not in the global external-identifier registry.
- If future product requirements need queryable market-membership history, design a separate temporal relationship/model; do not overload the current Entity columns in this packet.
