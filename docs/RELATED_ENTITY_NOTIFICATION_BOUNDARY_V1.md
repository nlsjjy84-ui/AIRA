# Related Entity Notification Boundary v1

## Core rule
Verified entity relationships do not expand Briefing or Alert trigger scope beyond the user's explicit Interest and the already-accepted qualifying Assessment rules.

## Trigger restraint
- Interest in COMPANY does not automatically activate alerts for linked SECURITY.
- Interest in SECURITY does not automatically activate alerts for linked COMPANY.
- A COMPANY↔SECURITY link may be shown as context inside an already-qualified Briefing/Alert item, but it is not itself a trigger.
- Related raw Facts, Evidence, or Material Events do not bypass the existing Assessment-gated Briefing/Alert semantics.

## User intent
The system must not infer that a user wants notifications about every related entity merely because an official relationship exists. Additional notification scope requires explicit user Interest/setting.

## Compatibility
This contract does not redesign accepted Briefing v1 or Alert v1 behavior; it only fixes the effect of future entity-linking on those existing semantics.

## Implementation boundary
Filtering queries, notification fan-out, UI context display, and regression tests are CODEX-FIRST/later work.