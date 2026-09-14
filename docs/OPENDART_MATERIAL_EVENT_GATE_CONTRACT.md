# OpenDART Material Event Gate Contract

## Evidence first
- Filing discovery/list rows are discovery metadata, not sufficient Event truth.
- Report title, `rm`, correction markers, company name, or keyword matching alone must never create or classify an Event.
- A material Event candidate requires a validated response from an explicitly approved OpenDART structured endpoint for that event responsibility.
- The endpoint response must resolve to the expected provider filing/company identity before Event registration.

## Event creation gate
- If endpoint identity or required structured fields are missing, malformed, ambiguous, or unsupported -> keep no fabricated Event; BLOCK that adapter responsibility.
- `CONFIRMED` Event still requires Evidence and Entity according to existing AIRA invariants.
- Do not create positive/negative investment direction, recommendation, or impact merely from a disclosure type.
- Event title must be neutral and factual.

## Time semantics
- Use an official endpoint field as `occurred_at` only when its provider meaning is explicitly the event's decision/occurrence/effective time for that adapter.
- Filing/receipt time is observation/provenance time and must not be silently substituted for event occurrence.
- If no approved event-occurrence field exists, leave `occurred_at` absent rather than guessing.
## Correction / repeated filing
- A later filing with a new `rcept_no` is new Evidence.
- Do not auto-merge, supersede, or replace an existing Event from title/`rm`/correction labels alone.
- Event dedup/merge may occur only under the already-accepted Event contract with explicit deterministic evidence; otherwise preserve separate Evidence and BLOCK lineage inference.

## Mapping rule before Codex implementation
- Each approved material endpoint must have, before implementation: endpoint identity, provider request/response contract, required fields, company/filing identity rule, EventType mapping, occurrence-time field (or explicit null), deterministic dedup inputs, and BLOCK cases.
- If any one of those is unresolved, Codex must not invent the adapter mapping.

## Current v1 candidate families
- capital increase/reduction
- merger/split/split-merger
- CB/BW/EB
- treasury-stock acquisition/disposal/trust
- business suspension/default/rehabilitation
- major asset or other-company equity acquisition/disposal
- major litigation

These are candidate families only until endpoint-specific contracts are approved.
