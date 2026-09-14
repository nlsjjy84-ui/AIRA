# Historical Backfill / Notification Boundary v1

## Core rule
과거 데이터를 오늘 수집·복구·backfill했다고 해서 그 과거 사건이 오늘 발생한 새 정보가 되지는 않는다.

## Meaning
- provider occurrence/reporting period and AIRA collection time remain distinct.
- historical Fact/Event/Assessment discovered later keeps its original provider time/period semantics.
- backfill ingestion time alone must not qualify an item as a fresh market Alert.
- reprocessing older Evidence or rebuilding indexes must not generate notification floods.

## Alert restraint
- existing Alert activation/baseline semantics remain authoritative.
- a historical item may appear in Inspect/Historical views when relevant, but must not be presented as `오늘 발생` solely because AIRA learned it today.
- any future product rule intentionally notifying users about newly discovered historical corrections must be explicit and separately identified as such.

## Briefing restraint
Historical backfill may enrich a briefing context, but collection time must not be substituted for the event/reporting time.

## Implementation boundary
Backfill scheduler, eligibility query, delivery suppression, replay markers and tests are CODEX-FIRST/later work.