# Provider Ingestion Common Contract

## Purpose
Apply the same AIRA trust/provenance rules across OpenDART, KRX, and ECOS without forcing provider-specific payloads into one generic DTO.

## Source
- Reuse one canonical Source identity per provider/service authority according to the existing registry.
- Do not create a new Source because a different endpoint was called.
- Never merge Sources across providers merely because content or company names match.

## Evidence identity
- Prefer a provider-native document/filing identifier when the provider actually supplies one and its semantics are stable.
- When the provider supplies no document ID, use an AIRA deterministic request/snapshot identity and explicitly document that it is not provider-native.
- Endpoint representations of the same provider filing do not automatically mean separate Evidence; follow the provider-specific Evidence identity contract.
- Distinct claim snapshots may be separate Evidence when their identity and responsibility are explicitly defined.

## Evidence conflict
- Same `(source, externalId, revision)` plus same canonical content/provenance -> reuse.
- Same identity/revision with different canonical content or provenance -> BLOCK.
- Never silently overwrite or auto-increment revision unless a later explicit provider revision contract authorizes it.
## Canonical hashing
- Exclude API secrets/auth keys and AIRA collection timestamps from provider content identity.
- Use stable field ordering and stable row ordering when provider row order is not semantically meaningful.
- Preserve semantically meaningful raw provider values; do not normalize by guessing names or units.

## Fact layer
- Provider codes/endpoint names/collection time stay out of provider-neutral Fact dedup identity unless the Fact meaning itself requires them.
- Equal provider-neutral Fact identity + equal value -> reuse Fact and add/reuse supporting assertion.
- Equal Fact identity + differing asserted value -> preserve both Evidence assertions and follow existing Fact conflict semantics; never overwrite the old value.
- A SUPPORTED Fact still requires Evidence-backed assertion.
- Exact period/unit context must use the already-approved provider-neutral relation/model rather than embedding provider strings into Fact.

## Event layer
- A CONFIRMED Event still requires Evidence and Entity.
- Provider filing identity may ensure v1 ingestion idempotency, but different provider filings are not automatically the same Event.
- Merge/supersession/correction lineage requires its own explicit evidence; never infer it from titles, labels, dates, or similar values alone.
## Network / transaction boundary
- Perform provider HTTP/network acquisition outside DB write transactions.
- Strictly validate and assemble provider-neutral input before opening the persistence transaction.
- Persist one coherent validated responsibility atomically; partial provider ingestion must roll back.
- Retries apply to safe provider acquisition boundaries, not by blindly replaying partially committed DB work.

## Implementation rule for Codex
- Reuse shared Source/Evidence/Fact registration primitives where semantics match.
- Keep endpoint DTO/parser validation provider-specific where field meaning differs.
- Do not build a new parallel provenance framework for ECOS, KRX, or OpenDART.
- Do not introduce a generic abstraction merely to reduce line count if it hides provider identity, unit, period, or conflict semantics.

## Verification cadence
- Validate one provider bundle with targeted tests after its coherent implementation is complete.
- Run the full API regression once after the combined provider bundle unless a concrete cross-cutting conflict requires earlier evidence.
