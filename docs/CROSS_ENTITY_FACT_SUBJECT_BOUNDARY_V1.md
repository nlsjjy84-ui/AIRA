# Cross-Entity Fact Subject Boundary v1

## Core rule
A verified relationship between entities does not transfer, merge, or rewrite Fact subject identity.

## Subject ownership
- OpenDART company financial Facts remain subject to canonical COMPANY.
- KRX listed-security market Facts remain subject to canonical SECURITY.
- ECOS country/macro statistical Facts remain subject to their approved statistical/context identity.
- COMPANY↔SECURITY linkage enables navigation/Relate context only; it does not change existing Fact subjects.

## Forbidden behavior
- do not re-subject SECURITY price/volume/market-cap Facts to COMPANY automatically;
- do not re-subject COMPANY revenue/operating-income Facts to SECURITY automatically;
- do not deduplicate Facts across different canonical subjects merely because entities are linked;
- do not copy Assertions/Evidence between entity subjects to make a unified synthetic Fact.

## Future aggregation
A combined company/security view may display related Facts together, but each value must retain its original subject, period, Evidence, and conflict status. Any derived cross-entity metric requires its own explicit derivation/provenance contract.

## Implementation boundary
This is domain/product meaning only. Queries, DTO composition, UI grouping, derived metrics, and tests are CODEX-FIRST/later work.