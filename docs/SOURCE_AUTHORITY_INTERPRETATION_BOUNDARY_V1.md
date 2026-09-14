# Source Authority Interpretation Boundary v1

## Core rule
A Source's authority applies only to the claim domain/scope for which that Source is officially authoritative. `Official` is not a universal truth or quality score.

## Examples
- OpenDART is authoritative for official corporate filings/disclosures within its contract scope.
- KRX is authoritative for exchange/listing/trading data within its approved scope.
- ECOS/BOK is authoritative for the approved official statistical series within its scope.

## Restraint
- Source authority does not permit unsupported cross-domain inference.
- An official source may still publish revised, corrected, missing, or conflicting observations; provenance must remain visible.
- Source role/authority must not become a recommendation score, trust percentage, or hidden weighting that silently selects one conflicting assertion.
- Conflicts between Evidence/Assertions are preserved until an explicit resolution contract exists.

## User-facing meaning
When displayed, describe the Source and its official role/scope neutrally. Do not translate `official` into `certain`, `good investment`, or `preferred outcome`.

## Implementation boundary
Authority-scope queries, badges, tooltips, conflict visualization, weighting, and tests are CODEX-FIRST/later work.