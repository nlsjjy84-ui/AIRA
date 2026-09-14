# Flow Context Continuity Boundary v1

## Core rule
AIRA preserves the user's explicitly selected canonical context while navigating `MAIN -> Ask -> Inspect -> Relate -> Assess`. Context changes require an explicit user action or an explicitly defined navigation action.

## Context that must not silently drift
- selected canonical Entity/subject;
- COMPANY vs SECURITY identity;
- Current vs Historical Exact mode when applicable;
- exact historical period/as-of target when selected;
- Ask perspective/analysis intent when carried forward;
- explicitly selected Evidence/Fact/Event focus where relevant.

## Forbidden behavior
- do not replace COMPANY with a linked SECURITY automatically;
- do not switch Historical Exact to Current because current data is easier to load;
- do not switch the selected entity because another related item ranks higher;
- do not silently drop the user's selected perspective between screens;
- do not create Interest merely because an entity becomes navigation context.

## User control
A context change should be visible and attributable to the user's selection. Related entities may be offered as navigation options without becoming the active context automatically.

## Implementation boundary
Route parameters, state stores, browser history, deep links, URL design, cache keys, and tests are CODEX-FIRST/later work.