# OpenDART Material Event Structured Evidence Fields Contract

## Identity
- EvidenceType = `OFFICIAL_DATA`.
- externalId = `OPENDART_MATERIAL:{endpointKey}:{rcept_no}`.
- revision = `1`; no automatic revision increment.
- Source is the existing OpenDART source; do not create one Source per endpoint.

## Stable provenance fields
- `originalUrl` = the official OpenDART structured endpoint base URL for that endpoint, with no API key and no request-window query string.
- Do not include `crtfc_key`, collection time, `bgn_de`, `end_de`, pagination, or other retrieval-window values in `originalUrl`.
- `locator` = deterministic receipt locator containing the approved `endpointKey` and exact 14-digit `rcept_no`.
- `title` = the neutral approved catalog label for the endpoint, not company name, amount, counterparty or sentiment text.
- `publishedAt` = null unless OpenDART explicitly supplies an actual publication timestamp for that structured representation.
- Never derive `publishedAt` from the digits of `rcept_no`, a decision date, or AIRA collection time.
- `collectedAt` = the actual AIRA collection timestamp; it is not provenance equality and may differ on idempotent recollection.
## Canonical content hash
- Hash the complete validated provider row for that receipt using deterministic field ordering.
- Exclude transport-wrapper `status`/`message`, request authentication material, collection timestamps, and response-row order.
- Do not hash only the Event-classification fields; the Evidence hash represents the validated structured row representation.
- Preserve provider strings exactly except normalization already required by the endpoint validation contract; do not trim/rename/translate arbitrary payload values solely to make hashes match.
- Same structured Evidence identity + same canonical row/provenance reuses the existing Evidence.
- Same identity + different canonical row/provenance BLOCKS; no overwrite and no revision bump.

## Relationship to raw filing Evidence
- Raw filing `DISCLOSURE / external_id=rcept_no` remains a separate filing provenance object.
- The structured material Evidence is the required SUPPORTS Evidence for Material Event registration.
- If raw filing Evidence already exists, the same Event may additionally link to it, but raw retrieval is not a prerequisite for confirming a valid structured Material Event.
