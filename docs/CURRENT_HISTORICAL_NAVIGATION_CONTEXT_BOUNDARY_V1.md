# Current / Historical Navigation Context Boundary v1

## Core rule
Moving between Current and Historical views must not silently change the selected Entity, Fact meaning, Event, or user-selected analytical perspective.

## Current → Historical
- preserve the selected canonical Entity;
- preserve the selected subject type (COMPANY vs SECURITY);
- require an explicit historical period/filing/date selection;
- do not replace the entity with a related COMPANY/SECURITY merely because a relation exists;
- do not reinterpret a Current Fact as Historical Exact without exact historical period evidence.

## Historical → Current
- return to the same canonical Entity and subject type;
- historical period context ends only because the user explicitly returns to Current;
- historical Evidence/Assessment remains inspectable and is never overwritten by Current state.

## Forbidden behavior
- no automatic COMPANY↔SECURITY substitution;
- no automatic nearest-period fallback;
- no silent carry-over of stale historical period labels into Current;
- no silent conversion of Current into Historical Exact.

## Implementation boundary
Routing, state stores, query parameters, cache behavior, UI controls, and tests are CODEX-FIRST/later work.