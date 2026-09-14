# OpenDART Material Event v1 — Official Field Matrix

Status: historical 24-endpoint field reference only. The approved P7 35+1 scope and static mapping in `OPENDART_DS005_35_PLUS_1_SCOPE.md` supersede endpoint scope here. Fields absent from this matrix remain unspecified; do not infer their meanings.
Source of truth: OpenDART DS005 developer guide, verified 2026-09-13.

## Common transport/identity
- The 24 historical endpoints listed here use `GET /api/{endpoint}.json`; the current approved 35+1 P7 scope is in `OPENDART_DS005_35_PLUS_1_SCOPE.md`.
- Request identity inputs: `corp_code` (8 digits), `bgn_de` and `end_de` (`YYYYMMDD` search window); `crtfc_key` is secret and never provenance identity.
- Every accepted row requires provider `rcept_no` exactly 14 digits and `corp_code` exactly 8 digits.
- `corp_name` and `corp_cls` are descriptive metadata, never Entity identity.
- Event identity remains `OpenDART + endpoint key + canonical COMPANY + rcept_no`.
- A later/different `rcept_no` is a different v1 Event unless a separately approved lineage rule exists.

## Date precision rule
- These APIs publish date fields, not provider timestamps.
- Never convert a date-only field to midnight/noon or another fabricated `occurred_at` timestamp.
- Preserve the validated official date in Evidence/adapter output; current Event `occurred_at` stays null until AIRA has an explicit date-precision representation.
- Receipt/search dates are provenance/observation metadata and must not substitute for event date.
## GOVERNANCE — 8 endpoints

| Endpoint | OpenDART apiId | Official event-date field | Rule |
|---|---:|---|---|
| `piicDecsn` | `2020023` | none in documented response | Do not infer a decision date; keep Event `occurred_at=null`. |
| `fricDecsn` | `2020024` | `bddd` | Official 이사회결의일(결정일); preserve as date-only evidence metadata. |
| `pifricDecsn` | `2020025` | `fric_bddd` | Only documented board-decision field in this combined response; do not invent a separate paid-in decision date. |
| `crDecsn` | `2020026` | `bddd` | Official 이사회결의일(결정일). |
| `tsstkAqDecsn` | `2020038` | `aq_dd` | Official 취득결정일; do not substitute acquisition-period dates. |
| `tsstkDpDecsn` | `2020039` | `dp_dd` | Official 처분결정일; do not substitute disposal-period dates. |
| `tsstkAqTrctrCnsDecsn` | `2020040` | `bddd` | Official 이사회결의일(결정일); `ctr_cns_prd` is contract 예정일, not event decision date. |
| `tsstkAqTrctrCcDecsn` | `2020041` | `bddd` | Official 이사회결의일(결정일); `cc_prd` is 해지예정일자, not event decision date. |
## BUSINESS — 12 endpoints

| Endpoint | OpenDART apiId | Official event-date field | Rule |
|---|---:|---|---|
| `cvbdIsDecsn` | `2020033` | `bddd` | Official 이사회결의일(결정일). |
| `bdwtIsDecsn` | `2020034` | `bddd` | Official 이사회결의일(결정일). |
| `exbdIsDecsn` | `2020035` | `bddd` | Official 이사회결의일(결정일). |
| `bsnInhDecsn` | `2020042` | `bddd` | Official 이사회결의일(결정일). |
| `bsnTrfDecsn` | `2020043` | `bddd` | Official 이사회결의일(결정일). |
| `tgastInhDecsn` | `2020044` | `bddd` | Official 이사회결의일(결정일). |
| `tgastTrfDecsn` | `2020045` | `bddd` | Official 이사회결의일(결정일). |
| `otcprStkInvscrInhDecsn` | `2020046` | `bddd` | Official 이사회결의일(결정일). |
| `otcprStkInvscrTrfDecsn` | `2020047` | `bddd` | Official 이사회결의일(결정일). |
| `cmpMgDecsn` | `2020050` | `bddd` | Official 이사회결의일(결정일); merger effective/schedule dates are not the decision date. |
| `cmpDvDecsn` | `2020051` | `bddd` | Official 이사회결의일(결정일); `dvdt` is 분할기일, not decision date. |
| `cmpDvmgDecsn` | `2020052` | `bddd` | Official 이사회결의일(결정일); later implementation/schedule dates are not substitutes. |
## RISK — 4 endpoints

| Endpoint | OpenDART apiId | Official event-date field | Rule |
|---|---:|---|---|
| `bsnSp` | `2020020` | `bsnspd` | Official 영업정지일자; `bddd` is board decision date and must not replace the suspension date for the RISK event. |
| `dfOcr` | `2020019` | `dfd` | Official 최종부도(당좌거래정지)일자. |
| `ctrcvsBgrq` | `2020021` | `rqd` | Official 신청일자. |
| `lwstLg` | `2020028` | `lgd` | Official 제기일자; `cfd` is 확인일자 and must not replace filing/occurrence date. |

## Adapter acceptance rule
- Validate the entire provider row before persistence; never infer missing identity/date fields from company name, receipt window, collection time, or another endpoint.
- Date field values are semantic evidence metadata, not Event dedup identity.
- A missing documented date does not justify a guessed timestamp. Preserve the raw row and use `occurred_at=null`; identity validation failures still BLOCK the row.
- `piicDecsn` is the explicit no-date exception in the official response schema.
- This historical matrix does not define fields for the newer approved endpoints. Add field semantics only after separate official verification.
