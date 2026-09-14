# OpenDART Material Event Request Contract

## Explicit request scope
Every approved v1 Material Event adapter accepts an explicit immutable request scope:
- `corpCode`: exactly 8 digits;
- `beginDate`: valid `YYYYMMDD`;
- `endDate`: valid `YYYYMMDD`;
- `beginDate <= endDate`;
- `beginDate >= 20150101` because OpenDART documents these major-report datasets as available from 2015.

Do not invent an implicit 'today', rolling lookback, scheduler cadence or historical backfill window inside the adapter/orchestrator. Those are caller/scheduling responsibilities outside P7.

## Provider request
- HTTP method = GET.
- Endpoint = the exact approved endpoint URL from the v1 field/catalog matrix.
- Provider-required query values are `crtfc_key`, `corp_code`, `bgn_de`, `end_de`.
- `crtfc_key` is secret transport material only; never include it in request descriptors persisted as provenance, logs, Evidence hash, `originalUrl`, or exception text.
- Do not substitute company name for `corp_code`.
## Window-independent receipt identity
- Request window is discovery scope, not Evidence/Event identity.
- The same `rcept_no` returned by overlapping valid request windows must resolve to the same structured Evidence/Event identities.
- `bgn_de`/`end_de` never participate in Material Event Evidence `externalId` or Event `dedup_key`.
- A different request window that returns identical canonical row content must be idempotent.

## Response boundary
- These adapters consume the provider `result.status/message/list` envelope; do not invent pagination where the official endpoint contract has none.
- Validate the complete returned list before entering any DB write transaction.
- `013` NO_DATA produces an empty validated result for that explicit request scope; it does not fabricate Evidence/Event rows.
- A malformed or divergent duplicate receipt in one response BLOCKS the affected response before persistence according to the orchestration contract.
