# Current Value Selection Boundary v1

## Core rule
`Current` is an explicit selection/view semantic; it is not permission to delete or overwrite historical records.

## Selection meaning
- a value may be shown as current only when an accepted AIRA contract defines why it is the current representation;
- recency alone is insufficient when the newer record is conflicting, malformed, unsupported, or lacks required provenance;
- historical exact views keep their filing/period identity even after newer observations arrive;
- conflicting candidates remain visible as conflict, not silently collapsed into one winner;
- current selection must never manufacture correction/supersession lineage that the provider did not establish.

## Separation
Current Assessment and Historical Exact Assessment remain distinct product concepts.
A current view may reference historical context, but must not rewrite the historical record.

## Implementation boundary
Current-selection query policy, supersession mechanics, caching, UI defaults, and tests are CODEX-FIRST/later work.