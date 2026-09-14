# P7 downstream integration BLOCK NOTE — 2026-09-14

Resolved by `P7_DOWNSTREAM_INTEGRATION_PASS_NOTE_2026-09-14.md` and additive V17. This note records the former blocker.

P7 exact receipt retry passes the existing downstream gates, but unrestricted historical backfill suppression is not proven and conflicts with the requested completion criterion.

P7 creates a CONFIRMED Event plus structured Evidence and no Assessment. Automatic Assessment preparation selects EARNINGS only. Current Assessment uses the unique supersession terminal; Historical Assessment preserves the requested ID. Briefing and Alert require COMPLETED Assessment plus Assessment Evidence. Reconciliation/dedup prevents repeated delivery for the same Assessment.

The P7 PostgreSQL test asserts zero Assessment, Briefing item, and Alert rows after initial registration and exact retry. Targeted PostgreSQL tests passed: P7 registration 5, Current Assessment 2, Historical Assessment 2, Briefing integrity 2, Alert integrity 2. Assessment rule and terminal-selector unit tests also passed. The optional EARNINGS preparation fixture test was skipped because its dedicated fixture flag was not enabled. Existing disposable PostgreSQL schema is at V16; no migration was changed.

Blocker: a newly created COMPLETED Assessment for a historical backfill can pass the existing Briefing `completed_at > effectiveStart` and Alert `completed_at > activatedAt` gates. Receipt/collection/Assessment completion time cannot establish that the underlying information is new. The current Event/Assessment schema has no explicit backfill/newness classification to distinguish that case, particularly for P7 events with `occurredAt=null`. Suppressing it without a new approved provenance contract and likely additive persistence would invent semantics. No heuristic based on EventType, timestamp recency, or receipt digits was added.
