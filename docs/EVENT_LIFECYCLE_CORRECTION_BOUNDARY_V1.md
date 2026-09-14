# Event Lifecycle / Correction Boundary v1

## Core rule
An Event that was validly observed is never deleted or silently rewritten merely because a later filing cancels, withdraws, corrects, or changes it.

## Meaning
- the original Event remains part of historical truth;
- a later cancellation/withdrawal/correction is represented as a distinct later Event or explicit relationship only when supported by official Evidence;
- `current relevance` may change, but historical existence does not disappear;
- current UI may de-emphasize a superseded/cancelled Event only while preserving access to the original Evidence/history.

## Forbidden behavior
- do not overwrite the original Event title/type/time/entity links with later filing data;
- do not mark two filings as the same Event merely because titles look similar;
- do not infer correction lineage from report title prefixes, `rm`, or textual similarity alone;
- do not delete prior Evidence/Assessment history when a later Event changes interpretation.

## Current-state meaning
AIRA may show that an earlier Event is no longer current/effective only when an explicit supported later relationship exists. Without that relationship, keep both observations independent.

## Implementation boundary
Relation schema, correction-link persistence, queries, UI chronology, migration, and tests are CODEX-FIRST/later work.