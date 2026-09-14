# Temporal Precision Presentation Boundary v1

## Core rule
AIRA preserves the temporal precision actually supplied or verified by the source. It must not manufacture more precise time information for display, ordering, or interpretation.

## Precision classes
- timestamp evidence/event data stays timestamp precision;
- provider date-only values stay date-only;
- exact reporting/statistical periods stay period start/end, not a synthetic timestamp;
- AIRA `collectedAt` / observation time is operational provenance and must not be presented as the provider occurrence/publication time.

## Forbidden behavior
- do not convert a date-only event to `00:00`/`12:00` and display it as an exact time;
- do not use collection time to fill missing provider occurrence time;
- do not collapse a fiscal/statistical period into its end timestamp when the user is inspecting period meaning;
- do not imply ordering precision finer than the underlying data supports.

## User-facing meaning
Labels and formatting should reflect the available precision: date, exact period, as-of, observed-at, or timestamp as appropriate.

## Implementation boundary
Formatting utilities, sort tie-breakers, DTO precision flags, UI labels, and tests are CODEX-FIRST/later work.