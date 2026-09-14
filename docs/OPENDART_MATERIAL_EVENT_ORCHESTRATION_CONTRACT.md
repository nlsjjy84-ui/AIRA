# OpenDART Material Event Orchestration Contract

## Network / validation boundary
For one approved material-event endpoint request:
1. execute HTTP outside any DB write transaction;
2. decode provider status and validate the complete response shape;
3. validate each row's `corp_code`, 14-digit `rcept_no`, endpoint-specific required fields, and official date fields;
4. reject duplicate `rcept_no` rows with divergent canonical content;
5. build one validated filing/Event input per receipt before persistence.

Discovery/report-title text does not bypass this structured validation boundary.

## Persistence granularity
Each validated `rcept_no` is an independent atomic persistence unit.
Persist one receipt per DB transaction so one bad filing does not roll back already-valid unrelated filings returned in the same provider response.
Within that transaction:
- resolve/reuse the canonical OpenDART COMPANY mapping;
- register/reuse the endpoint-specific structured OFFICIAL_DATA Evidence (`OPENDART_MATERIAL:{endpointKey}:{rcept_no}`);
- register/reuse the deterministic material Event;
- register/reuse COMPANY `SUBJECT` and Evidence `SUPPORTS` links;
- confirm the Event only after both links exist.
## Company identity prerequisite
- Material Event orchestration requires an already-resolved canonical COMPANY ↔ `OPENDART/CORP_CODE` mapping before endpoint persistence.
- Resolve by explicit `OPENDART/CORP_CODE`, never by `corp_name`.
- Existing mapping must point to `EntityType.COMPANY`; otherwise BLOCK.
- If the mapping is absent, BLOCK this ingestion responsibility and let the caller run the already-approved company-directory/bootstrap responsibility separately before retrying.
- Never invoke company bootstrap/network discovery inside the Material Event per-receipt DB transaction.
- Every provider row `corp_code` must equal the request/mapped corp code; mismatch BLOCKS that receipt.

## Re-run / conflict
- Same receipt + same canonical structured Evidence + same endpoint Event dedup identity is idempotent.
- Same structured Evidence identity with different canonical content/provenance BLOCKS before Event mutation.
- Same Event dedup identity must not create duplicate EventEntity/EventEvidence rows.
- A later correction/new filing with a different receipt remains a separate Evidence/Event in v1.

## Transaction failure
Any failure after entering the per-receipt transaction rolls back Evidence/Event/link creation for that receipt. No HTTP request or provider retry occurs inside the DB transaction.
