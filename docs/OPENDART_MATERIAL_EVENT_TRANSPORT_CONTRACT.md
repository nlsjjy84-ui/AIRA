# OpenDART Material Event Transport Contract

## Reuse existing provider error model
Use existing `OpenDartProviderException.Category`; do not add a parallel Material Event exception hierarchy.

Provider application status mapping:
- `000` -> success; validate response/list rows.
- `013` -> `NO_DATA`; this is an empty query result, not Evidence and not an Event.
- `010`, `011`, `012`, `901` -> `AUTHENTICATION`.
- `020` -> `RATE_LIMIT`.
- `021`, `100`, `101` -> `INVALID_REQUEST`.
- `014`, `800`, `900` -> `PROVIDER_FAILURE`; do not silently reinterpret these as a valid empty result.
- Unknown non-`000` status -> `PROVIDER_FAILURE` and retain only safe status/message diagnostics.

HTTP non-2xx -> `PROVIDER_FAILURE` unless an already-existing shared transport rule gives a more specific category.
I/O/interruption -> existing `TRANSPORT` behavior; preserve interrupt status on interruption.
Malformed JSON/envelope -> `MALFORMED_RESPONSE`.
## Request contract
- Endpoint is exactly `/api/{approvedEndpointKey}.json` from the approved P7 35+1 DS005 scope; the 24-row field matrix is historical and cannot exclude newly approved endpoints.
- Request requires `crtfc_key`, `corp_code`, explicit `bgn_de`, explicit `end_de`.
- Validate `corp_code` as 8 digits and dates as real `YYYYMMDD` values with `bgn_de <= end_de` before HTTP.
- Do not invent an implicit today/backfill window inside the adapter; caller supplies the bounded window.
- No DB write transaction may span the HTTP request.

## Secret handling
- OpenDART requires `crtfc_key` as a query parameter, but the key is transport secret only.
- Never include the key or full credential-bearing URI in logs, exceptions, Evidence URL/externalId, canonical hash, provenance, diagnostics, or test snapshots.
- Safe diagnostics may include endpoint key, corp code, date window, HTTP status and provider application status/message.
## Row validation / duplicate policy
- Envelope failure blocks the whole HTTP result before any persistence.
- After a valid `000` envelope, validate rows into independent receipt inputs.
- A row with invalid `rcept_no`, invalid `corp_code`, incompatible endpoint shape, or identity mismatch is rejected for that receipt and must not reach DB persistence.
- Other independently valid receipts from the same response may continue; do not roll them back because one unrelated receipt is malformed.
- Duplicate rows with the same `(endpointKey, corp_code, rcept_no)` and identical canonical content collapse to one validated input.
- Duplicate rows with the same identity but different canonical content BLOCK that receipt identity; do not choose one by order.
- Return/report rejected receipt diagnostics explicitly; never silently skip malformed rows as if ingestion succeeded.
