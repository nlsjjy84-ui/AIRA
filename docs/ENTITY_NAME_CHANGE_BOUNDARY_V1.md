# Entity Name Change Boundary v1

## Core rule
Display-name changes do not create, merge, or remap canonical Entity identity by themselves.

## Identity preservation
- canonical identity continues through official name/display-label changes when the approved external identifier remains the same;
- COMPANY identity is not re-created merely because `corp_name` changes;
- SECURITY identity is not re-created merely because `ISU_NM`, abbreviation, or English name changes;
- names are never substitutes for approved external identifiers.

## Historical provenance
- Evidence retains the provider text/name observed in that historical snapshot;
- updating current display metadata must not rewrite old Evidence, assertions, or provenance;
- a historical name may be shown as historical context without becoming a second canonical Entity.

## Restraint
A changed name plus a changed identifier does not prove continuity or discontinuity by itself. Corporate/entity lineage under identifier changes requires a separate explicit evidence-backed contract; do not infer it from text similarity.

## Implementation boundary
Alias/history tables, metadata refresh, rename UI, lineage modeling, and tests are CODEX-FIRST/later work.