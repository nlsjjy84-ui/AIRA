# MAIN Discovery Boundary v1

## Role
MAIN is a card-based discovery/return surface for the user's explicit context. It is not a recommendation feed or stock-picking screen.

## Eligible reasons to surface an item
- explicit user Interest;
- newly available qualifying Briefing/Alert state under the already-accepted contracts;
- a user-selected recent entity/context that supports continuation of the AIRA flow;
- system-required account/service state that needs user attention.

## Forbidden meaning
- card prominence/order must not imply buy/sell preference, expected return, quality ranking, or hidden AI conviction;
- price momentum, popularity, engagement, or sponsorship cannot silently become recommendation signals;
- related entities do not enter MAIN merely because they are linked to an interested entity;
- raw Evidence or unqualified Events do not bypass accepted Briefing/Alert/Assessment gates.

## Explainability
When an item appears because of Interest, Briefing, Alert, or a continuation context, the UI should be able to state that reason in neutral language.

## Implementation boundary
Feed queries, ranking/tie-breaking, card layout, recency policy, pagination, caching, and tests are CODEX-FIRST/later work.