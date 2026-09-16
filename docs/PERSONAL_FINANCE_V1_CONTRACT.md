# AIRA Personal Finance v1 Contract

Status: CLOSED for foundation scope (2026-09-16)

## Purpose
AIRA keeps its existing official market/company analysis core and adds a separate user-owned personal-finance bounded context.
This extension supports the final-project themes of personalized budget/spending analysis and MyData-ready financial management without relabeling public OpenDART/KRX/ECOS data as MyData.

## v1 foundation scope
- user-owned finance data connection
- account/card display unit
- income/expense transaction
- monthly total/category budget
- demo import now; real MyData provider integration later

## Hard boundaries
- personal transactions never enter the public canonical Fact/Evidence tables.
- raw account numbers, raw card numbers, provider credentials and access tokens are not stored.
- provider/account/transaction external identifiers are stored only as stable hashes where identity is required.
- every account and transaction is bound to exactly one app user; cross-user linkage is blocked by database constraints.
- deleting an app user cascades to personal-finance rows.
- `DEMO_IMPORT` must be visibly identified as demo data; it must not be presented as a live MyData connection.

## Analysis boundary
- deterministic spending aggregation/pattern detection may run as `RULE` and must be labeled as such.
- an analysis is labeled `AI` or `HYBRID` only after a real configured AI provider execution succeeds.
- existing `AIExecution` is execution-provenance infrastructure, not proof that an AI provider is currently connected.
- AI output explains patterns and evidence; it does not set budgets, recommend purchases, or make investment decisions for the user.

## Delivery sequence
1. PF-1 foundation schema + JPA/repository integrity.
2. PF-2 authenticated demo import + monthly summary/budget APIs.
3. PF-3 spending-pattern events and evidence-linked RULE analysis.
4. PF-4 actual AI explanation adapter + AIExecution provenance.
5. PF-5 personal-finance UI integrated without replacing MAIN/Ask/Inspect/Relate/Assess.
