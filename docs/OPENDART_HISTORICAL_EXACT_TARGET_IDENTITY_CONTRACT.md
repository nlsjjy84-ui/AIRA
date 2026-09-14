# OpenDART Historical Exact Target Identity Contract

## Problem
Current `OpenDartAnnualCfsContext` carries corp code/year/report/CFS scope but not the Filing Discovery `rcept_no`.
Historical Exact must not allow a downstream structured API response to silently choose a different filing than the one Discovery selected.

## Authoritative target
Before annual CFS value extraction, Filing Discovery must produce one validated target containing at minimum:
- canonical COMPANY / validated 8-digit `corp_code`;
- `bsns_year` request scope;
- annual report code `11011`;
- provider-native 14-digit `rcept_no`;
- validated filing date/provenance metadata from Discovery.

The discovered `rcept_no` is the expected filing identity for every downstream OpenDART representation in this packet.

## Downstream identity gate
- Annual CFS response rows used for Facts must have `rcept_no == expected discovered rcept_no`.
- Filter/validate by expected receipt BEFORE account-id selection; do not select an account from another receipt and then adopt that receipt as the target.
- All supported metric rows for the filing must use the same expected receipt.
- Period-witness resolution must target the same expected receipt.
- Any downstream response exposing a different receipt is `PERIOD_WITNESS_IDENTITY_MISMATCH`/identity BLOCK, not a fallback to the provider's latest filing.
## Codex implementation boundary
- Add the smallest additive carrier needed for expected receipt identity; do not redesign closed Filing Discovery or the existing company identity model.
- It is acceptable to extend the prepared-filing/orchestration input or add a dedicated discovered-target value object; do not overload provider display text as identity.
- Preserve existing annual CFS request transport parameters (`corp_code`, `bsns_year`, `reprt_code=11011`, `fs_div=CFS`); the expected receipt is an AIRA validation constraint, not an invented provider request parameter.
- The filing `DISCLOSURE` Evidence external ID remains the validated expected `rcept_no`.

## BLOCK / idempotency
- No matching expected receipt in the annual CFS response -> BLOCK/NO_DATA according to the settled provider contract; never substitute another receipt.
- Multiple incompatible rows for the expected receipt/account -> MALFORMED_RESPONSE/BLOCK.
- Same expected receipt + same validated content/period -> idempotent reuse.
- A correction/new filing with a different receipt is a different target and is never merged automatically.
