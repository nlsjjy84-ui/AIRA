# Historical Context Selection Boundary v1

## Core rule
When the user explicitly enters a Historical Exact context, AIRA must preserve that historical scope until the user changes it.

## Product meaning
- Historical Exact is not automatically replaced by Current during navigation;
- Ask / Inspect / Relate / Assess must preserve the selected Entity + exact period context unless the user chooses another context;
- current values may be shown as a clearly labeled comparison, never as a silent replacement;
- related Entity navigation must not silently change Historical Exact into Current.

## Restraint
Do not use collection recency, latest Assessment, latest filing, or current market data to overwrite the user's selected historical scope.

## Presentation rule
Always make the active period/context visible enough that the user can tell whether they are viewing Current or Historical Exact.

## Implementation boundary
Routing state, URL/query state, selectors, persistence, and tests are CODEX-FIRST/later work.