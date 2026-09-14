# Historical Exact + type-specific Current — targeted PASS / KRX Current HOLD

> KRX Current HOLD here was resolved for approved KOSPI/KOSDAQ v1 by `KRX_CURRENT_INTEREST_ELIGIBILITY_PASS_NOTE_2026-09-14.md`.

- Financial Historical Exact requires a canonical COMPANY, explicit start/end, exact 14-digit OpenDART receipt, supported value Evidence from that receipt, and matching filing-specific `FactPeriodEvidence` from the annual CFS witness. A missing period, receipt, or witness link returns an exact miss; there is no nearest-period or fiscal-year inference.
- Financial Current is selection-only at this boundary: it requires an explicit company, period and receipt and delegates to Historical Exact verification. It does not choose a latest annual filing or use current `acc_mt`.
- Assessment Current loads the Event's completed Assessment graph, uses the existing unique-terminal selector, and then reads that exact assessment. Forked/broken lineage does not choose by timestamp or ID. Historical Assessment lookup by `assessmentId` remains unchanged.
- KRX Current is HOLD: repository contracts define exact `basDd` snapshot ingestion and forbid forward-fill, but do not define how to establish the latest completed official trading day. No D-1 fallback or provider re-call was introduced.
- Targeted checks passed: PostgreSQL provider gate with exact filing/period/witness-link success and miss cases; H2 Current Assessment chain/fork and existing terminal/historical tests. The four-class targeted run passed 9 tests; the final Evidence-ID join was rechecked by the PostgreSQL gate test. No migration or full regression was run.
