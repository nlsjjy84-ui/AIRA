# Negative Evidence Boundary v1

## Core rule
A query returning no matching filing/event/data does not automatically prove that the real-world condition or event did not exist.

## Distinguish meanings
- provider-confirmed NO_DATA for a bounded request scope;
- AIRA has not collected that scope yet;
- collection failed;
- the queried endpoint does not cover the relevant event type;
- the event/fact is affirmatively reported as absent under an explicit official contract.

Only the last case supports a true negative claim.

## Forbidden behavior
- do not translate empty result into `사건 없음` without an explicit bounded negative-evidence contract;
- do not infer `문제 없음` from lack of material-event rows;
- do not infer `0` from missing Facts;
- do not hide coverage limitations when presenting absence.

## Product meaning
AIRA should say `확인된 자료 없음` or equivalent bounded language when absence is about evidence coverage, not reality.

## Implementation boundary
Coverage-aware negative queries, absence labels, UI, and tests are CODEX-FIRST/later work.