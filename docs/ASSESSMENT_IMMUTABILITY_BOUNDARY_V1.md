# Assessment Immutability Boundary v1

## Core rule
A COMPLETED Assessment is a historical analytical record of the evidence/context used at completion time. It is not silently rewritten when newer Evidence, Facts, or Events arrive.

## Product meaning
- preserve the completed Assessment's evidence set and meaning;
- new material evidence may justify a new Assessment or explicit supersession path, but not an in-place semantic rewrite;
- Current Assessment selection follows the accepted explicit-supersession contract; simple `latest wins` is not sufficient;
- Historical Exact Assessment remains bound to its exact event/evidence/period context;
- if newer information invalidates confidence, expose the new state explicitly rather than editing history invisibly.

## Restraint
Do not mutate old Assessment conclusions merely to make old screens agree with new data.
Do not erase prior reasoning/evidence provenance.

## Implementation boundary
Assessment version creation, supersession orchestration, query selection, UI history, and tests are CODEX-FIRST/later work.