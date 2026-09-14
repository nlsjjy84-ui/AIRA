# P7 downstream integration PASS NOTE — 2026-09-14

V17 adds immutable first-ingestion `event.ingestion_origin`: `LIVE`, `BACKFILL`, or `LEGACY_UNKNOWN`. Existing rows receive `LEGACY_UNKNOWN`; caller paths without an explicit origin also fail closed to that value. OpenDART DS005 request → validated receipt → P6 Event now carries an explicit origin, and the earnings normalization boundary can carry one too. The dedup upsert preserves the original value on retries, even if a later request supplies a different origin.

Automatic Briefing and Alert candidate queries accept only `LIVE` CONFIRMED Events with a real COMPLETED, Evidence-backed Assessment. `completed_at` remains an eligibility cutoff for Briefing but is not the newness comparison; the assessment's creation time crosses the user's interest/briefing period. BACKFILL and LEGACY_UNKNOWN remain in Event, Assessment, Evidence and Historical queries. P7 does not automatically create an Assessment.

Targeted disposable PostgreSQL tests passed: P7 registration 6 (including LIVE/BACKFILL first-origin preservation and retry), Briefing integrity 3, Alert integrity 3. The delivery suites assert BACKFILL and LEGACY_UNKNOWN yield no automatic items, LIVE yields one, and retry reuses it. Flyway validated 17 migrations; the test database reports version 17 successful. No earlier migration changed.
