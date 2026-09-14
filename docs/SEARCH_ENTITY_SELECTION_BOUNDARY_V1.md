# Search Entity Selection Boundary v1

## Core rule
Header search resolves and presents canonical entities; it does not merge identities by display text.

## COMPANY vs SECURITY
- COMPANY and SECURITY remain distinct selectable results even when names are identical or similar.
- A verified COMPANY↔SECURITY relation may be shown as context, but does not collapse them into one identity.
- Search never infers a relation from name similarity, ticker text, abbreviation, or ranking proximity.
- Selecting a COMPANY opens the company-oriented flow; selecting a SECURITY opens the security-oriented flow.

## Interest consequence
Interest applies only to the entity the user explicitly selected. Search selection does not auto-add Interest to a linked COMPANY/SECURITY counterpart.

## Product restraint
Search is navigation/discovery, not a recommendation engine. Result order or prominence must not be described as investment preference, quality, or AI judgment unless a separately approved contract defines such semantics.

## Implementation boundary
This document fixes selection meaning only. Search index design, ranking, API queries, autocomplete, UI components, and tests are CODEX-FIRST/later implementation work.