# Product State / Error Boundary v1

## Core rule
AIRA distinguishes data state, contract BLOCK, factual conflict, and temporary provider/transport failure. They are not one generic `error` state.

## Product meanings
- `NO_DATA` / unavailable: nothing was returned/verified under the specific query contract; does not prove zero/false/nonexistence.
- `BLOCKED`: AIRA intentionally refused to assert/persist/present a stronger claim because a required invariant or identity/evidence condition was not satisfied.
- `CONFLICTING`: multiple preserved assertions disagree for one Fact identity; no winner was chosen.
- provider/transport failure: the requested verification could not complete because the external source/request failed; underlying fact state remains unknown.
- malformed/identity mismatch: provider data was returned but failed the approved contract; do not downgrade this to ordinary no-data.

## User-facing restraint
Show a concise, neutral reason appropriate to the state. Do not expose API keys, stack traces, DB details, internal file paths, or security-sensitive configuration.

## Recovery meaning
Retry is appropriate only for retryable provider/transport conditions. BLOCKED/CONFLICTING states require new valid evidence, corrected identity, or an explicit future resolution rule rather than blind retry loops.

## Implementation boundary
Exception mapping, HTTP status codes, retry policy, UI components, logging/telemetry, and tests are CODEX-FIRST/later work.