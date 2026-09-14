# Date / Timezone Presentation Boundary v1

## Core rule
Provider dates and timestamps keep their original semantic precision and timezone context; UI localization must not alter the underlying event/reporting date meaning.

## Date-only values
- a provider date such as `YYYY-MM-DD` remains date-only;
- do not attach a user-local timezone and shift it to another calendar date;
- do not fabricate midnight timestamps for sorting/display.

## Timestamp values
- preserve the source/normalized timezone or offset used by the accepted contract;
- user-local display may be offered only as presentation, with the original instant remaining unchanged.

## Product meaning
Reporting period dates, event occurrence dates, publication time, observation time, and collection time are distinct concepts and must not be merged.

## Forbidden behavior
Do not let timezone conversion make a filing/event appear to belong to a different reporting/trading date.

## Implementation boundary
Timezone conversion utilities, locale formatting, sorting helpers, UI, and tests are CODEX-FIRST/later work.