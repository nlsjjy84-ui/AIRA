# 12D visualization read API contract

All endpoints are public GET reads over persisted canonical Facts. They do not ingest, call a provider, choose a fiscal period, discover KRX D, or create a Fact. Each response contains `state` and `reason` (null on success). Frontend charts use only returned numbers, dates, and Evidence IDs; missing observations are gaps, not zeroes.

## Financial exact A ↔ B

`GET /api/companies/{companyId}/financial-facts/compare?aStart=YYYY-MM-DD&aEnd=YYYY-MM-DD&aReceipt=14digits&bStart=YYYY-MM-DD&bEnd=YYYY-MM-DD&bReceipt=14digits&predicates=REVENUE`

Default predicates are the currently supported `REVENUE` and `OPERATING_INCOME`. Each requested predicate is read through `FinancialHistoricalExactQuery` independently for A and B, requiring an exact receipt, supported Fact, and verified `FactPeriodEvidence` on both sides. If any requested side is absent or ambiguous, the response has no `metrics`; `reason` names the A/B failure. Same-currency values are required.

For each metric, `changeAmountBMinusA = B - A`. `changePercentBOverA = (B - A) / A × 100`, rounded to four decimal places, only if A is positive. A zero or negative baseline returns null percent and `percentReason=BASE_NON_POSITIVE`; amount remains available. The API does not label this as year-over-year or quarter-over-quarter because period comparability has no approved classification contract. A/B carry exact dates, receipts, raw values, currency, and Evidence ID lists.

## Stored KRX official range series

`GET /api/securities/{securityId}/market-series?predicate=CLOSE_PRICE&from=YYYY-MM-DD&to=YYYY-MM-DD`

Only canonical active KOSPI/KOSDAQ SECURITY and the eight approved stored market predicates are supported. Every point is a supported numeric Fact with a matching same-date official KRX daily Evidence assertion. `points` are ascending by actual `tradingDate`; each has `factId`, raw `value`, `evidenceIds`, and `evidenceExternalId`. Missing dates are omitted, never interpolated. Conflicting Facts or multiple Facts on one date block the range.

## D versus previous official observation

`GET /api/securities/{securityId}/market-previous?predicate=CLOSE_PRICE&currentDate=YYYY-MM-DD&currentFactId=UUID`

The caller supplies the date and Fact ID returned by the existing `/market-current` API. This read verifies that exact D Fact in the stored official series, then selects the immediately preceding actual stored official observation date. It does **not** independently establish D or substitute D-1 if the supplied D Fact is missing. Both points return exact dates, Fact IDs, Evidence IDs, and raw values. `changeAmount = current - previous`; percent divides by previous only when previous is positive. A missing current or previous point returns `NO_DATA` with an exact reason. UI must display `previous.tradingDate` when presenting the comparison.

The current-day resolver remains the existing API contract. The range and previous endpoints never call KRX transport. For OHLC, call the range endpoint per predicate and render a candle only when all four values share each actual date. For financial bars and numeric delta, use this API response; do not compute or relabel growth in the browser.
