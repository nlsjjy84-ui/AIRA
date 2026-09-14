# Generic Event Registration v1 Contract

## Goal
Add one provider-neutral registration primitive for non-EARNINGS material Events without weakening the accepted EARNINGS path.

## Candidate input
A generic candidate requires:
- approved `EventType`;
- neutral nonblank title;
- optional provider-precision `occurredAt` (null is valid);
- nonnull AIRA `observedAt`;
- deterministic nonempty `dedupKey`;
- nonnull `now`.

The generic factory must not accept Event status, merge/supersession state, Entity links or Evidence links as caller-controlled creation values.
New candidates begin `CANDIDATE`, exactly like EARNINGS.

## Registration / concurrency
- Reuse the existing unique `event.dedup_key` constraint as the creation arbiter.
- Registration must run inside an existing write transaction and hold the winning Event row lock through relation creation and confirmation.
- Generic SQL/registration must insert the candidate's actual `event_type`; never hardcode `EARNINGS`.
- On dedup conflict, reuse/lock the existing Event rather than creating a second row.
## Reuse integrity checks
After dedup reuse/lock:
- existing `event_type` must equal the requested type;
- existing title must equal the deterministic neutral title for that dedup contract;
- do not rewrite an existing Event merely to make it match a changed adapter mapping;
- type/title mismatch for the same dedup key is a contract conflict and BLOCKS that registration.

## Observation lifecycle
- Re-observation may advance `last_observed_at` only through the existing Event lifecycle semantics.
- Do not change `first_observed_at` on reuse.
- Do not re-open or mutate `MERGED`/`DISCARDED` Events through ingestion.
- `CONFIRMED` reuse is idempotent after relation checks; do not downgrade it to `CANDIDATE`.

## Confirmation boundary
- Creation/reuse alone never confirms an Event.
- Confirmation occurs only after at least one canonical Entity link and one supporting Evidence link exist, preserving current AIRA invariant.
- Material Event v1 uses canonical COMPANY `SUBJECT` + structured OFFICIAL_DATA `SUPPORTS`.
- Any failure before both links/confirmation completes rolls back the packet transaction.
