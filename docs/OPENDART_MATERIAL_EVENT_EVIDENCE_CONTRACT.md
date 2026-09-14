# OpenDART Material Event Structured Evidence Contract

## Responsibility split
- Provider-native filing identity remains `rcept_no` (14 digits).
- Existing raw filing Evidence keeps `EvidenceType.DISCLOSURE` and `external_id = rcept_no` where that filing-provenance path registers it.
- Material Event structured API output is a distinct official claim snapshot and MUST NOT reuse the raw filing Evidence content/hash slot.

## Structured Evidence identity
- `EvidenceType = OFFICIAL_DATA`.
- Deterministic AIRA external identity: `OPENDART_MATERIAL:{endpointKey}:{rcept_no}`.
- This external identity is AIRA-defined and must never be described as provider-native.
- `revision = 1` because these structured endpoints expose no provider revision number.
- Source remains the existing OpenDART/regulator Source; do not create one Source per endpoint.

## Canonical content
- Hash the complete validated provider row for that endpoint/receipt using deterministic field ordering.
- Exclude `crtfc_key`, collection time, request-window `bgn_de/end_de`, transport formatting, and JSON member order from content identity.
- Preserve provider strings/values exactly after structural validation; do not rewrite company names or date strings for hashing.
- Same structured Evidence identity + same canonical content/provenance -> reuse.
- Same structured Evidence identity + different canonical content/provenance -> BLOCK; no overwrite and no automatic revision increment.
## Event support linkage
- The structured OFFICIAL_DATA Evidence is the mandatory `EventEvidence(SUPPORTS)` input for Material Event v1 registration.
- Event dedup identity still uses provider `rcept_no` + endpoint + canonical COMPANY; Evidence external identity does not replace Event identity.
- A separately registered raw filing DISCLOSURE Evidence may also be linked to the Event when already available/verified, but Material Event confirmation does not depend on that extra representation.
- Do not create a duplicate raw-`rcept_no` Evidence row from the structured endpoint response.

## Correction boundary
- A later provider filing with a different `rcept_no` creates a different structured Evidence identity and different v1 Event unless an explicit future lineage rule proves otherwise.
- Do not infer correction lineage from report title, `rm`, or text markers.
