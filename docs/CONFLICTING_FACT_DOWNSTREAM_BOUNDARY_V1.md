# Conflicting Fact Downstream Boundary v1

## Purpose
A Fact marked `CONFLICTING` preserves unresolved official/provider assertions. It is not a resolved truth value.

## Downstream rule
- `CONFLICTING` Facts remain queryable/inspectable with all assertions and Evidence preserved.
- No downstream component may silently pick one asserted value as the canonical value.
- `CONFLICTING` Facts must not be treated as resolved SUPPORTED input for Assessment computation.
- They must not directly trigger Briefing/Alert as if a single factual value were established.
- Existing accepted Briefing/Alert semantics remain unchanged; this contract only forbids treating unresolved raw Fact conflict as settled truth.

## Resolution restraint
- AIRA does not auto-resolve by newest Evidence, Source preference, row order, ingestion time, larger/smaller value, or provider-specific heuristics.
- A later assertion with a different value does not overwrite earlier assertions.
- Any future conflict-resolution mechanism requires its own explicit product contract and provenance.

## User-facing meaning
- If exposed in Inspect/Relate later, the state should communicate that authoritative assertions disagree or remain unresolved.
- It must not be framed as a recommendation, prediction, or hidden AI choice.

## Implementation boundary
This is a product/domain meaning contract only. Query changes, Assessment guards, UI treatment, and regression tests are CODEX-FIRST or later implementation work.