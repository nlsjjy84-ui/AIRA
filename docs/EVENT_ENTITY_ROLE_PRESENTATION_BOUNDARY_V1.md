# Event Entity Role Presentation Boundary v1

## Core rule
An Entity linked to an Event must be presented according to its explicit role, not merely because it appears in the same event record.

## Product meaning
- SUBJECT means the Event is about that Entity under the registered event contract;
- other future roles must remain distinct rather than being flattened into one participant list;
- COMPANY and SECURITY identities remain separate even when related;
- Event membership does not imply ownership, causality, beneficiary status, or responsibility unless that role is explicitly modeled and evidenced.

## Restraint
Do not infer a role from entity name, row order, title wording, or relationship proximity.
Do not promote a related COMPANY/SECURITY into the Event merely because the other side is linked elsewhere in AIRA.

## Presentation rule
When role meaning is unavailable, present only the verified association actually modeled; do not invent a stronger relationship label.

## Implementation boundary
Role schema expansion, queries, UI labels, and tests are CODEX-FIRST/later work.