# Information Ordering / Priority Boundary v1

## Core rule
`latest`, `current`, `important`, and `user-relevant` are different meanings and must not be collapsed into one hidden ranking.

## Meanings
- Latest: newest by the correct provider/event/period chronology.
- Current: the value/Assessment explicitly valid under the current-selection/supersession contract.
- Important: a product interpretation requiring explicit criteria; not inferred from recency alone.
- User-relevant: tied to explicit user context/Interest/selection; not a recommendation score.

## Presentation rules
- If ordering is chronological, label/use chronology only.
- Current truth may be older than the newest collected Evidence.
- Newly collected historical/backfill data does not outrank current information merely because collection time is recent.
- User Interest may filter/scope information but must not manufacture factual importance.
- AIRA must not imply `top`, `best`, `most important`, or recommendation without an explicit approved rule.

## Restraint
Do not use collection timestamp, click/popularity, commercial value, or unexplained AI score as a universal priority ranking.

## Implementation boundary
Ranking algorithms, query ORDER BY, scoring, UI grouping, and tests are CODEX-FIRST/later work.