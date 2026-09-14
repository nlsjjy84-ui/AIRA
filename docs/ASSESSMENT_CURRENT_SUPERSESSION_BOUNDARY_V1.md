# Assessment Current / Supersession Boundary v1

## Core rule
For one Event, Current Assessment selection and historical Assessment preservation are separate responsibilities.

## Current Assessment
- at most one Assessment may be presented as Current for an Event;
- Current is established only by the accepted explicit supersession/current-selection semantics;
- newest created_at or newest collected Evidence alone must not decide Current;
- COMPLETED status alone does not make an Assessment Current.

## Supersession
- a new Assessment may supersede a prior Current Assessment only through explicit lineage/selection;
- supersession does not delete, rewrite, or detach the prior Assessment or its Evidence;
- prior Assessment remains inspectable as historical reasoning/evidence state;
- supersession is not equivalent to declaring the prior Assessment false.

## Historical Exact
Historical Exact Assessment is selected by its exact historical context and must not silently replace the Event's Current Assessment.

## Forbidden behavior
- no `ORDER BY created_at DESC LIMIT 1` as product meaning;
- no destructive replacement of prior Assessment;
- no automatic supersession merely because new Evidence arrived;
- no merging two Assessments into one synthetic result without an explicit contract.

## Implementation boundary
Schema changes, queries, services, lineage wiring, UI, migrations, and tests are CODEX-FIRST/later work.