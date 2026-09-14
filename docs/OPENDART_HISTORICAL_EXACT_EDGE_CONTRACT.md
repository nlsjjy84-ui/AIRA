# OpenDART Historical Exact Edge Contract

## Purpose
Close only the remaining historical-period ambiguity before Codex implementation. This does not reopen 1A, Filing Discovery, or the existing period-witness contract.

## Authority order
1. `rcept_no` identifies the filing.
2. The same filing's validated annual CFS period witness determines the exact historical period.
3. `company.json.acc_mt` is current official company metadata and may support discovery/plausibility, but it must not override a filing-specific historical witness.
4. `bsns_year` identifies the provider fiscal/business year request scope; it is not sufficient by itself to manufacture period start/end.

## Historical fiscal-year change
- Never project the current `acc_mt` backward across historical filings.
- A historical filing whose official witness ends in a month different from current `acc_mt` is not a conflict by itself.
- A change in fiscal year-end may create a non-12-month transition period.
- If the matched annual CFS witness supplies one exact consistent duration, accept that duration even when it is shorter or longer than 12 months.
- Do not normalize, pad, truncate, or convert such a duration into a conventional 12-month year.
## BLOCK conditions
- No exact duration row in the matched annual CFS witness -> BLOCK.
- More than one distinct exact duration in the same matched witness -> BLOCK.
- Witness `corp_code`, `bsns_year`, `reprt_code`, or `rcept_no` differs from the discovered filing identity -> BLOCK.
- Adapter-produced Fact period differs from the resolved witness period -> BLOCK before persistence.
- Missing/invalid current `acc_mt` does not justify guessing; use filing-specific witness or BLOCK.

## Filing identity across endpoints
- Discovery `rcept_no` must be exactly 14 digits.
- Any downstream OpenDART response that exposes `rcept_no` must match the discovered filing exactly.
- Original disclosure document retrieval must use that same `rcept_no`.
- Company name, report title, filing date, `rm`, or correction-like labels are never substitutes for receipt identity.

## Codex consequence
Codex must preserve the existing witness resolver behavior and must not add a 12-month invariant. If current code derives a historical period from present-day `acc_mt` when a filing-specific witness is available, replace that derivation with the witness result instead of adding compatibility logic.
