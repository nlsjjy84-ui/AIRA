# Inspect v1 Product Boundary

## Role
`Inspect / 살피기` is the evidence drill-down for a selected entity, Fact, Event, or claim. It is not another search page and not an AI answer screen.

## Must expose
- selected canonical subject/entity identity;
- Fact/Event type and current status;
- exact period/date precision available from the source;
- supporting Assertions/Evidence and their source identity;
- conflict/missing/blocked state when applicable;
- provenance locator sufficient to understand where the claim came from.

## Product behavior
- SUPPORTED/CONFIRMED does not mean investment recommendation or certainty beyond the recorded evidence.
- CONFLICTING values remain visibly conflicting; Inspect never chooses a hidden winner.
- missing data is shown as missing/unsupported, never coerced to zero or a guessed value.
- COMPANY and SECURITY remain distinct subjects even when related.

## Navigation
Inspect may continue to `Relate` to view verified relationships/context or to `Assess` when an eligible Assessment exists. It does not auto-create an Assessment from inspection.

## Implementation boundary
DTOs, evidence pagination, visual hierarchy, components, caching, queries, and tests are CODEX-FIRST/later implementation work.