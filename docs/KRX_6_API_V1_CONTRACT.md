# KRX 6 API v1 Contract

## Scope
Approved/requested v1 endpoints:
- `stk_isu_base_info` — KOSPI security base information
- `ksq_isu_base_info` — KOSDAQ security base information
- `stk_bydd_trd` — KOSPI daily stock trading
- `ksq_bydd_trd` — KOSDAQ daily stock trading
- `kospi_dd_trd` — KOSPI-series daily index data
- `kosdaq_dd_trd` — KOSDAQ-series daily index data

All calls use an explicit `basDd` (`YYYYMMDD`). Authentication material is transport-only and must never enter Evidence content/hash/provenance.

## Security identity
The base-info endpoints are authoritative for KRX security identity.
- base `ISU_CD` = provider standard code; this is the canonical KRX security external identifier.
- base `ISU_SRT_CD` = provider short code; retain as a secondary external identifier scoped to the KRX market.
- Daily stock `ISU_CD` is the short stock code in that response, not the base endpoint's standard-code field.
- Join daily rows to base-info rows only by endpoint-market pair + `daily.ISU_CD == base.ISU_SRT_CD` for the same requested `basDd`.
- Missing or non-unique code mapping BLOCKS that observation; names never repair identity.
- `ISU_NM`/abbreviations are display metadata only, never identity keys.
- Do not infer or create COMPANY–SECURITY linkage from security/company names.
## Daily stock Market Fact v1
Promote only these provider fields to Fact:
- `TDD_OPNPRC` -> `OPEN_PRICE` (KRW)
- `TDD_HGPRC` -> `HIGH_PRICE` (KRW)
- `TDD_LWPRC` -> `LOW_PRICE` (KRW)
- `TDD_CLSPRC` -> `CLOSE_PRICE` (KRW)
- `ACC_TRDVOL` -> `TRADING_VOLUME` (shares; no currency)
- `ACC_TRDVAL` -> `TRADING_VALUE` (KRW)
- `MKTCAP` -> `MARKET_CAP` (KRW)
- `LIST_SHRS` -> `LISTED_SHARES` (shares; no currency)

`CMPPREVDD_PRC` and `FLUC_RT` remain Evidence fields in v1; do not store them as source Facts because they are derivable from exact source Facts.

Market Fact invariants:
- subject Entity type = `SECURITY`.
- no Event is required or synthesized for a daily market observation.
- exact observation date = `BAS_DD`; store `period_start == period_end == BAS_DD`.
- price/value/capitalization currency is fixed to `KRW` by this endpoint contract.
- volume/share predicates have no currency; their unit meaning is fixed by predicate semantics.
- provider-reported values are stored exactly as official daily values; do not back-adjust, corporate-action-adjust, or infer adjusted history.
- malformed/blank required numeric values BLOCK that specific Fact registration; do not silently coerce, guess, or derive replacements.
## Market Fact model extension
Current AIRA code does not yet support these predicates. Codex must implement one coherent provider-neutral market-fact extension rather than KRX-specific Fact subclasses.
- Add exactly the eight v1 predicates above to `FactPredicate`.
- Add a market-number Fact construction path that permits `SECURITY` subjects, `event == null`, one-day periods, and predicate-specific currency requirements.
- Keep existing earnings and REAL_GDP construction rules unchanged.
- Add a provider-neutral market Fact dedup key based on canonical security identity + predicate + trading date + semantic unit/currency contract.
- Reuse existing `FactAssertion` provenance. One daily endpoint response snapshot may support many rows and many metric Facts.
- Same Fact asserted with a different value by different admissible Evidence becomes `CONFLICTING`; never overwrite the original assertion.

## Evidence snapshot contract
Each endpoint + explicit `basDd` call is one KRX Evidence snapshot.
- Source = existing `EXCHANGE` KRX source.
- EvidenceType = `OFFICIAL_DATA`.
- deterministic AIRA request identity: `KRX_OPENAPI:{apiId}:{basDd}`; this is not a provider-native document ID.
- revision = 1; provider exposes no revision here.
- same source/externalId/revision + same canonical validated content/provenance -> reuse.
- same identity + different canonical content/provenance -> BLOCK; no overwrite and no auto revision increment.
- content hash covers the full validated response with stable field and row ordering and excludes AUTH_KEY, collection time, and transport row order.
- every returned `BAS_DD`, where present, must equal the explicit requested `basDd`.
## Index endpoints: Evidence-only in v1
`kospi_dd_trd` and `kosdaq_dd_trd` return `IDX_CLSS` and `IDX_NM` but no verified stable provider-native index code in the approved response contract.
Therefore:
- register and validate the full endpoint response as KRX Evidence snapshots under the same snapshot/idempotency rules.
- `IDX_CLSS` + `IDX_NM` may be used only as deterministic row-sorting/canonicalization fields inside that snapshot.
- do not create canonical INDEX/MARKET Entity identity from `IDX_NM`, `IDX_CLSS`, or their concatenation.
- do not promote index rows to Fact until a stable official identifier and entity mapping contract is separately verified.
- do not silently special-case the displayed names `KOSPI` or `KOSDAQ` as permanent identifiers.

## Validation boundaries
- base-info standard code and short code must be nonblank; ambiguous duplicates within the same market/date BLOCK identity registration.
- daily stock row must resolve to exactly one base-info security by code, not by name.
- daily numeric source fields are strict provider numeric strings after whitespace normalization; no comma stripping or fuzzy cleanup unless the official contract explicitly permits it.
- zero is a valid numeric observation where returned by the provider; do not turn zero into missing.
- stock basic-info fields such as `PARVAL`, names, section, and listing date remain identity/display metadata in this v1 packet; do not invent extra Fact predicates.

## Explicit non-goals
- no COMPANY–SECURITY relationship inference.
- no ETF/ETN/ELW, derivatives, KONEX, or other KRX endpoints.
- no calculated return/change-rate Fact.
- no index Fact until stable official identity is solved.
- no scheduler/backfill policy in this packet.

## Canonical row ordering and FactAssertion locator
Canonicalization is dataset-specific but deterministic:
- `stk_isu_base_info` / `ksq_isu_base_info`: sort validated rows by base `ISU_CD` (standard code), then stable full-field serialization.
- `stk_bydd_trd` / `ksq_bydd_trd`: after identity resolution, sort by resolved canonical standard code; the transport short code remains preserved in canonical row content.
- `kospi_dd_trd` / `kosdaq_dd_trd`: sort only inside the Evidence snapshot by `IDX_CLSS`, then `IDX_NM`, then stable full-field serialization. This sorting key is not Entity identity.

For stock Market Facts, `FactAssertion.locator` must identify the validated source row and field deterministically, for example:
`OutBlock_1/ISU_SRT_CD={shortCode}/{providerField}`.
The locator may use the transport short code only because the row has already been resolved uniquely to a canonical SECURITY; it must not replace canonical Fact identity.

Base-info requests have no response `BAS_DD` field in the approved schema; their Evidence request identity still includes the explicit request `basDd`. Daily/index responses that expose `BAS_DD` must match the requested date on every row.
## Exact API IDs / approved paths (2026-09-13 verification)
All six requested services use HTTP `GET`, query parameter `basDd=YYYYMMDD`, and HTTP header `AUTH_KEY`.
Approved base host: `https://data-dbg.krx.co.kr`.
JSON response rows are under `OutBlock_1`.

- KOSPI stock base info: API ID `stk_isu_base_info`, path `/svc/apis/sto/stk_isu_base_info`.
- KOSDAQ stock base info: API ID `ksq_isu_base_info`, path `/svc/apis/sto/ksq_isu_base_info`.
- KOSPI stock daily trade: API ID `stk_bydd_trd`, path `/svc/apis/sto/stk_bydd_trd`.
- KOSDAQ stock daily trade: API ID `ksq_bydd_trd`, path `/svc/apis/sto/ksq_bydd_trd`.
- KOSPI index daily series: API ID `kospi_dd_trd`, path `/svc/apis/idx/kospi_dd_trd`.
- KOSDAQ index daily series: API ID `kosdaq_dd_trd`, path `/svc/apis/idx/kosdaq_dd_trd`.

Do not substitute sample paths in production. Sample URLs use `/svc/sample/apis/...`; production ingestion uses `/svc/apis/...` only.
Do not send the API key as a query parameter or persist it in Evidence/provenance/log content.
## Exact response field sets
Stock daily (`stk_bydd_trd`, `ksq_bydd_trd`) validated fields:
`BAS_DD, ISU_CD, ISU_NM, MKT_NM, SECT_TP_NM, TDD_CLSPRC, CMPPREVDD_PRC, FLUC_RT, TDD_OPNPRC, TDD_HGPRC, TDD_LWPRC, ACC_TRDVOL, ACC_TRDVAL, MKTCAP, LIST_SHRS`.

Stock base info (`stk_isu_base_info`, `ksq_isu_base_info`) validated fields:
`ISU_CD, ISU_SRT_CD, ISU_NM, ISU_ABBRV, ISU_ENG_NM, LIST_DD, MKT_TP_NM, SECUGRP_NM, SECT_TP_NM, KIND_STKCERT_TP_NM, PARVAL, LIST_SHRS`.

Index daily (`kospi_dd_trd`, `kosdaq_dd_trd`) validated fields:
`BAS_DD, IDX_CLSS, IDX_NM, CLSPRC_IDX, CMPPREVDD_IDX, FLUC_RT, OPNPRC_IDX, HGPRC_IDX, LWPRC_IDX, ACC_TRDVOL, ACC_TRDVAL, MKTCAP`.

## Missing / malformed value policy
- Required request/identity fields must be valid: explicit `basDd`, response `BAS_DD` where present, and required security identifiers. Identity/date mismatch BLOCKS the whole snapshot before DB write.
- Never coerce provider `-`, blank, or null numeric text to zero.
- For a validated stock row, `-`/blank/null in one supported numeric metric means no Fact/Assertion is created for that metric; the original token remains preserved in the shared Evidence snapshot.
- A non-empty numeric token that is not parseable under the documented numeric format is malformed and BLOCKS the whole snapshot; it is not silently skipped.
- Missing/malformed fields required to resolve SECURITY identity BLOCK the snapshot rather than dropping that row.
- Index responses remain Evidence-only in v1, so all validated raw fields are preserved even when individual numeric fields have no value.
## Deterministic Evidence identity / canonicalization
Use the exact KRX API ID as `datasetKey` in Evidence request identity:
`KRX_OPENAPI:{apiId}:{basDd}`.
Examples: `KRX_OPENAPI:stk_bydd_trd:20260911`, `KRX_OPENAPI:ksq_isu_base_info:20260911`.

Canonical content hash includes the full validated `OutBlock_1` representation, not only the fields promoted to Facts.
- Preserve provider text tokens exactly after transport decoding, including `-` for no-value fields.
- Exclude AUTH_KEY, collection time, HTTP header ordering and provider row ordering.
- Field order is the documented endpoint response-field order fixed in this contract.
- Stock daily rows sort by validated `ISU_CD`, then full canonical row as deterministic tie-breaker; duplicate identical identity rows BLOCK rather than relying on provider order.
- Stock base-info rows sort by validated standard `ISU_CD`, then short `ISU_SRT_CD`, then full canonical row; duplicate/conflicting identifier rows BLOCK.
- Index rows sort by `IDX_CLSS`, then `IDX_NM`, then full canonical row. This ordering is for snapshot hashing only and does not promote names to canonical Entity identity.
- Request `basDd` is part of canonical request provenance even for endpoints whose rows do not echo `BAS_DD`.

Same `(Source, externalId, revision=1)` plus identical canonical hash/provenance reuses Evidence. Different canonical content/provenance under the same identity BLOCKS; no overwrite and no automatic revision increment.
## Promotion boundary by endpoint
Stock base-info endpoints are identity/metadata Evidence in v1.
- Use `ISU_CD`, `ISU_SRT_CD` and official display/market metadata for SECURITY bootstrap/reuse.
- Do not promote base-info `PARVAL` or base-info `LIST_SHRS` into Facts in v1.
- Preserve all validated base-info fields in the Evidence snapshot.

Stock daily-trade endpoints are the only KRX v1 source promoted to the eight Market Fact predicates.
- Promote only `TDD_OPNPRC`, `TDD_HGPRC`, `TDD_LWPRC`, `TDD_CLSPRC`, `ACC_TRDVOL`, `ACC_TRDVAL`, `MKTCAP`, `LIST_SHRS`.
- Preserve `CMPPREVDD_PRC` and `FLUC_RT` in Evidence but do not create Fact predicates for them in v1.
- Preserve name/market/section fields as provenance/context only; they are not identity substitutes or Facts.

This boundary avoids duplicate Fact promotion from base-info and daily-trade representations while retaining the complete official Evidence snapshots.
## HTTP / response validation contract
- Method: GET.
- Request parameter: exactly one explicit `basDd` in `YYYYMMDD`; reject null, malformed or implicit/latest-date requests before HTTP.
- Authentication: `AUTH_KEY` header only. Never put the key in URL, Evidence, hash input, provenance text, application log or exception message.
- Evidence `original_url` may include the production endpoint and non-secret `basDd` query only.
- HTTP 401/403 -> authentication BLOCK; 429 -> rate-limit/transient BLOCK; other 4xx -> invalid-request BLOCK; 5xx/network/timeout -> provider-failure BLOCK.
- A 2xx body must parse as the documented response object and contain `OutBlock_1` as an array. Missing/wrong-type output block BLOCKS as malformed.
- An empty `OutBlock_1` is a valid official no-data snapshot, not malformed. It may persist as Evidence with zero promoted Facts.
- For stock daily/index rows, every present `BAS_DD` must exactly match requested `basDd`; any mismatch BLOCKS the whole snapshot.
- For a stock market/date packet: daily rows with data require a non-empty same-date base-info mapping sufficient to resolve every short code. If daily is empty, no SECURITY/Fact creation is required.
- Do not silently retry with another date when a requested date has no data.

## Retry restraint
Transport-level retries, if existing infrastructure already provides them, may retry only the exact same idempotent GET request. They must not alter `basDd`, endpoint, dataset or parsing rules. No fallback to sample endpoints or latest available date.
## KRX Source registration
Use exactly one provider Source for this v1 KRX ingestion bundle:
`SourceRegistration(SourceType.EXCHANGE, "krx", "KRX Data Marketplace Open API", "openapi.krx.co.kr")`.

- Do not create one Source per API ID, market, or dataset.
- API/dataset identity belongs in Evidence `externalId` (`KRX_OPENAPI:{apiId}:{basDd}`), not in Source identity.
- Production Evidence URLs may use the official data host `data-dbg.krx.co.kr`; that does not create a second Source.
- If an existing Source with the same external key has conflicting type/name/domain provenance, BLOCK rather than silently creating `krx2` or another duplicate provider row.
## Source authority scopes
The single KRX Source is an `OFFICIAL_OPERATOR` for two provider-wide v1 scopes:
1. `AuthorityScopeType.LISTING_STATUS` + `AuthorityRole.OFFICIAL_OPERATOR` for stock base-info services.
2. `AuthorityScopeType.MARKET_TRADING_DATA` + `AuthorityRole.OFFICIAL_OPERATOR` for stock daily-trade and index daily-series services.

For these provider-wide scopes in v1:
- `subject_entity_id = null`.
- `jurisdiction_entity_id = null`; no additional jurisdiction semantics are invented in this packet.
- `basis_url` is the official KRX Open API service-list/detail provenance, with the non-secret official service list acceptable as common basis.
- Re-registration of the same logical scope must be idempotent; conflicting duplicate scope rows must not be silently created.
- Do not create separate authority scopes per SECURITY, API row or trading date.
