# COMPANY–SECURITY Cardinality & Selection Boundary v1

## Core rule
A canonical COMPANY may be related to multiple canonical SECURITY entities. The relationship must not be modeled or presented as inherently one-to-one.

## Cardinality meaning
- multiple listed securities/share classes for one COMPANY are valid;
- each SECURITY remains its own canonical subject and retains its own KRX identity/Facts;
- a relationship does not merge those securities or their market histories;
- historical issuer/security relationship changes require explicit temporal evidence/modeling and must not be inferred from current metadata.

## Default-selection restraint
- AIRA does not silently choose one linked SECURITY as the COMPANY's `primary`, `main`, or `representative` security in v1;
- opening/selecting a COMPANY must not auto-redirect to one linked SECURITY;
- when security-specific market data is needed, the selected SECURITY must be explicit or governed by a separately approved deterministic contract;
- ranking, liquidity, market cap, ticker familiarity, or search popularity cannot silently define the primary security.

## Implementation boundary
Relationship tables, cardinality constraints, temporal issuer history, primary-security policy, UI selectors, and tests are CODEX-FIRST/later work.