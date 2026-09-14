# OpenDART Filing Evidence Identity Contract

## Core identity
- One OpenDART filing is identified by provider-native `rcept_no` (14 digits).
- For the annual CFS value path, the filing Evidence keeps `external_id = rcept_no` and `EvidenceType.DISCLOSURE`.
- This Evidence represents the filing/provenance identity, not a separate Evidence row for every transport representation of that filing.
- The original disclosure viewer/document URL belongs to the same filing identity.

## Representation rule
- `list.json`, structured financial APIs, disclosure viewer, and original-document retrieval may be different representations/access paths of the same filing.
- Do not create multiple Evidence rows with the same OpenDART source + `rcept_no` + revision merely because a different endpoint was used.
- The filing Evidence hash may be computed from the validated canonical provider representation used for value assertions; it must not be described as a hash of raw original-document bytes unless those bytes were actually hashed.
- If the same filing identity/revision is recollected with different canonical content or provenance, existing Evidence conflict semantics BLOCK; do not overwrite or auto-increment revision.
## Separate Evidence is allowed only for a separate claim snapshot
- The annual CFS period witness is separate Evidence because it is a different official-data response supporting the exact-period claim.
- Its deterministic `fnlttSinglAcnt:CFS:11011:{rcept_no}` identity is AIRA request identity, not a provider-native document ID.
- Additional future endpoint snapshots may become separate Evidence only when they support a distinct claim/provenance responsibility and use a non-colliding deterministic identity.

## Original document verification
- If Codex adds original-document retrieval, it verifies/enriches the same filing provenance; it must not silently register a second raw-`rcept_no` Evidence row.
- Retrieval must use the same discovered 14-digit `rcept_no`.
- Failure to retrieve or verify the original document must never be represented as successful verification.
- If raw-document archival/hash provenance is later required, that is a separate model decision; do not retrofit it by changing the existing filing Evidence identity.

## Corrections
- A correction with a new provider `rcept_no` is a new filing Evidence identity.
- Do not infer correction lineage from report title, `rm`, or textual markers alone.
- Never overwrite Historical Evidence/Fact/Assessment solely because another filing appears later.
