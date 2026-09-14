# OpenDART Material Event v1 Catalog

> Superseded scope: this 24-endpoint catalog is historical. P7 automatic registration follows the approved 35+1 DS005 scope in `OPENDART_DS005_35_PLUS_1_SCOPE.md`; its endpoint/EventType/title mapping takes precedence. Do not infer missing field semantics from this older catalog.

## AIRA mapping decision
This is an AIRA product mapping, not a provider-supplied classification.

### GOVERNANCE
Capital/shareholder-structure decisions:
- `piicDecsn` 유상증자 결정
- `fricDecsn` 무상증자 결정
- `pifricDecsn` 유무상증자 결정
- `crDecsn` 감자 결정
- `tsstkAqDecsn` 자기주식 취득 결정
- `tsstkDpDecsn` 자기주식 처분 결정
- `tsstkAqTrctrCnsDecsn` 자기주식취득 신탁계약 체결 결정
- `tsstkAqTrctrCcDecsn` 자기주식취득 신탁계약 해지 결정

### BUSINESS
Financing/restructuring/asset-allocation decisions:
- `cvbdIsDecsn` 전환사채권 발행결정
- `bdwtIsDecsn` 신주인수권부사채권 발행결정
- `exbdIsDecsn` 교환사채권 발행결정
- `cmpMgDecsn` 회사합병 결정
- `cmpDvDecsn` 회사분할 결정
- `cmpDvmgDecsn` 회사분할합병 결정
- `bsnInhDecsn` 영업양수 결정
- `bsnTrfDecsn` 영업양도 결정
- `tgastInhDecsn` 유형자산 양수 결정
- `tgastTrfDecsn` 유형자산 양도 결정
- `otcprStkInvscrInhDecsn` 타법인 주식 및 출자증권 양수결정
- `otcprStkInvscrTrfDecsn` 타법인 주식 및 출자증권 양도결정

### RISK
Adverse operating/legal/solvency processes:
- `bsnSp` 영업정지
- `dfOcr` 부도발생
- `ctrcvsBgrq` 회생절차 개시신청
- `lwstLg` 소송 등의 제기

### Not used for this v1 subset
- `DISCLOSURE` is not a fallback for an unresolved structured material event.
- `POLICY_REGULATION` and `MARKET` are not used by these company-filed OpenDART adapters.
- Any new material-report endpoint outside this catalog requires its own mapping decision before Event registration.
## Common identity and idempotency
- Every adapter requires provider `rcept_no` exactly 14 digits and `corp_code` exactly 8 digits.
- The response company must resolve through the explicit `CORP_CODE -> COMPANY` mapping; company-name matching is forbidden.
- Same `(OpenDART, endpoint key, rcept_no, company)` is one v1 material Event registration identity and must be idempotent.
- Different `rcept_no` values remain separate v1 Events unless an already-approved explicit merge/lineage rule proves otherwise.
- If one receipt appears as incompatible rows in more than one catalog endpoint, BLOCK classification rather than create multiple competing Events.

## Evidence
- Material Event support uses endpoint-specific structured `OFFICIAL_DATA` Evidence under `OPENDART_MATERIAL_EVENT_EVIDENCE_CONTRACT.md`.
- Structured Evidence identity is `OPENDART_MATERIAL:{endpointKey}:{rcept_no}`; it is AIRA-defined, not provider-native.
- Existing raw filing `DISCLOSURE / external_id=rcept_no` Evidence remains a separate filing-provenance responsibility and is not overwritten by structured endpoint content.
- Same structured Evidence identity/revision with different canonical content/provenance BLOCKS; no overwrite or automatic revision increment.

## Observation time
- Provider filing date is provenance metadata, not automatically event occurrence time.
- When OpenDART supplies no precise filing timestamp, do not manufacture one from the date alone; AIRA collection/observation time may populate observation tracking while the provider date remains provider metadata.
## `occurred_at` rule
- Decision endpoints use only the provider field explicitly documented as the decision date for that adapter (commonly `bddd`; special fields such as treasury `aq_dd` / `dp_dd` are used when documented).
- `bsnSp`: use official `bsnspd` (영업정지일자) as the occurrence date; do not substitute `bddd` when representing the suspension event itself.
- `dfOcr`: use official `dfd` (최종부도/당좌거래정지 일자).
- `ctrcvsBgrq`: use official `rqd` (신청일자).
- `lwstLg`: use official `lgd` (제기일자).
- If the documented occurrence/decision field is blank or malformed, leave no guessed occurrence timestamp and BLOCK adapter registration when the field is required by that adapter contract.

## Codex boundary
- Implement only this catalog first; do not expand to all 36 OpenDART material-report APIs.
- One shared transport/error/identity skeleton is preferred over 24 copy-pasted clients, while endpoint DTO/validation stays explicit enough to preserve provider field meaning.
- Finish adapter bundle before targeted tests; do not run the whole API suite after each endpoint.
